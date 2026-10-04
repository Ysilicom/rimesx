import AppKit
import Foundation

/// The grammar and file-format rules behind Capsule's structured notes and
/// passwords. Uses a private temporary library; never prints a credential.
func runCapsuleStructureSmokeTest() -> Bool {
    func fail(_ message: String) -> Bool {
        print("FAILED: capsule structure \(message)")
        return false
    }

    // Header: what RIMES writes and what Obsidian's Properties editor writes.
    let header = [
        "capsule: note",
        "version: 1",
        "id: \"00000000-0000-4000-8000-000000000001\"",
        "title: 阿里云服务器",
        "summary: '杭州机房的 ''主'' 服务器'",
        "IP: 47.96.1.2",
        "tags:",
        "  - 服务器",
        "  - 运维",
        "aliases: [阿里云]",
        "",
        "端口: \"22\" ",
    ]
    guard let parsed = try? CapsuleFrontMatter.parse(header) else {
        return fail("Obsidian multi-line properties are readable")
    }
    guard parsed.scalar("title") == "阿里云服务器",
          parsed.scalar("id") == "00000000-0000-4000-8000-000000000001",
          parsed.scalar("summary") == "杭州机房的 '主' 服务器",
          parsed.scalar("tags") == nil,
          parsed.userFields == [CapsuleField(label: "IP", value: "47.96.1.2"),
                                CapsuleField(label: "端口", value: "22")] else {
        return fail("scalars decode and only one-line user properties become fields")
    }
    guard parsed.preservedLines == ["IP: 47.96.1.2", "tags:", "  - 服务器", "  - 运维",
                                    "aliases: [阿里云]", "端口: \"22\" "] else {
        return fail("keys RIMES does not own are kept verbatim")
    }
    guard (try? CapsuleFrontMatter.parse(["title: a", "title: b"])) == nil,
          (try? CapsuleFrontMatter.parse(["  - orphan"])) == nil,
          (try? CapsuleFrontMatter.parse(["no colon here"])) == nil,
          (try? CapsuleFrontMatter.parse(["url: https://a.b"]))?.scalar("url") == "https://a.b" else {
        return fail("duplicates and orphans are malformed; a colon inside a value is not a key")
    }

    // Body grammar.
    let body = """
    # 服务器
    - IP：10.0.0.1
    - 密码: hunter2
    * 一行说明
    1. 第一步
    - [ ] 待办事项
    > 引用一行
    这是一句很长很长的话：它包含冒号但不是字段因为前面的部分太长了超过了二十四个字
    - https://example.com/path
    ```
    ssh root@10.0.0.1
    exit
    ```
    ## 空分组
    """
    let sections = CapsuleEntryGrammar.sections(body: body)
    let rows = sections.flatMap(\.rows)
    guard sections.map(\.heading) == ["服务器", "空分组"],
          rows == [
            .field(CapsuleField(label: "IP", value: "10.0.0.1")),
            .field(CapsuleField(label: "密码", value: "hunter2")),
            .line("一行说明"),
            .line("第一步"),
            .line("待办事项"),
            .line("引用一行"),
            .line("这是一句很长很长的话：它包含冒号但不是字段因为前面的部分太长了超过了二十四个字"),
            .line("https://example.com/path"),
            .code("ssh root@10.0.0.1\nexit"),
          ] else {
        return fail("body divides into headings, fields, lines and blocks: \(rows.count) rows")
    }
    guard rows.map(\.copyText)[0] == "10.0.0.1",
          CapsuleField(label: "密码", value: "x").isSecret,
          CapsuleField(label: "API Key", value: "x").isSecret,
          !CapsuleField(label: "Keyboard", value: "x").isSecret,
          !CapsuleField(label: "用户名", value: "x").isSecret,
          CapsuleField(label: "网址", value: "https://a.b").isURL else {
        return fail("fields copy only their value and secrets are recognised by label")
    }
    guard CapsuleEntryGrammar.fallbackSummary(body: "- 密码：x\n- 用户：isaac") == "用户 isaac",
          CapsuleEntryGrammar.fallbackSummary(body: "# 标题\n正文") == "正文",
          CapsuleEntryGrammar.mayContainSecret(body: "账号 root 密码 password"),
          !CapsuleEntryGrammar.mayContainSecret(body: "- IP：10.0.0.1"),
          CapsuleEntryGrammar.removingEmptyFields(CapsuleEntryGrammar.passwordTemplate + "- 备注：保留")
            .trimmingCharacters(in: .whitespacesAndNewlines) == "- 备注：保留" else {
        return fail("fallback summaries skip secrets; empty template fields are dropped")
    }
    guard CapsuleSummaryRules.normalized("  一行\n两行  ") == "一行 两行",
          CapsuleSummaryRules.normalized(" \n ") == nil,
          CapsuleSummaryRules.leaksSecret(summary: "我的密码是 hunter2", body: "- 密码：hunter2"),
          !CapsuleSummaryRules.leaksSecret(summary: "公司邮箱", body: "- 密码：hunter2") else {
        return fail("summaries are one line and never hold a secret value")
    }

    // RFC 6238 appendix B, SHA-1 seed "12345678901234567890" at T = 59 s.
    guard let totp = CapsuleTOTP.parameters(
            label: "两步验证",
            value: "otpauth://totp/x?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&digits=8"
          ),
          CapsuleTOTP.code(totp, at: Date(timeIntervalSince1970: 59)).code == "94287082",
          CapsuleTOTP.code(totp, at: Date(timeIntervalSince1970: 59)).remaining == 1,
          CapsuleTOTP.parameters(label: "TOTP", value: "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ") != nil,
          CapsuleTOTP.parameters(label: "备注", value: "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ") == nil,
          !CapsuleField(label: "TOTP", value: "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ").isSecret else {
        return fail("one-time codes follow RFC 6238")
    }

    // Stores.
    let root = FileManager.default.temporaryDirectory
        .appendingPathComponent("rimes-capsule-structure-\(UUID().uuidString)")
    defer { try? FileManager.default.removeItem(at: root) }
    do {
        let content = CapsuleContentStore(rootURL: root)
        let saved = try content.put(CapsuleContentWriteRequest(
            type: .note, title: "服务器", content: "- IP：10.0.0.1", summaryText: "  杭州\n机房 "
        ))
        var record = try content.record(id: saved.id)
        guard record.summaryText == "杭州 机房", record.cardSummary == "杭州 机房" else {
            return fail("note summary round-trips as one line")
        }
        // Obsidian adds a list property and a user property to the file.
        var text = try String(contentsOf: saved.fileURL, encoding: .utf8)
        text = text.replacingOccurrences(
            of: "updated_at:",
            with: "tags:\n  - 服务器\n机房: 杭州\nupdated_at:"
        )
        try text.write(to: saved.fileURL, atomically: true, encoding: .utf8)
        record = try content.record(id: saved.id)
        guard record.formatIssue == nil,
              record.headerFields == [CapsuleField(label: "机房", value: "杭州")] else {
            return fail("an Obsidian-edited note still reads, with its property as a field")
        }
        _ = try content.put(CapsuleContentWriteRequest(
            id: saved.id, type: .note, title: "服务器", content: "- IP：10.0.0.2"
        ))
        let rewritten = try String(contentsOf: saved.fileURL, encoding: .utf8)
        guard rewritten.contains("tags:\n  - 服务器\n机房: 杭州"),
              !rewritten.contains("summary:"),
              try content.record(id: saved.id).content == "- IP：10.0.0.2" else {
            return fail("saving keeps Obsidian's keys and drops a cleared summary")
        }
        let broken = content.entryDirectoryURL
            .appendingPathComponent("\(UUID().uuidString.lowercased()).md")
        try "---\ntitle: 坏掉的笔记\n这一行没有冒号\n---\n正文".write(to: broken, atomically: true, encoding: .utf8)
        let listed = try content.listRecords()
        guard let degraded = listed.first(where: { $0.summary.fileURL.lastPathComponent == broken.lastPathComponent }),
              degraded.formatIssue != nil,
              degraded.summary.title == "坏掉的笔记",
              degraded.content.contains("这一行没有冒号"),
              listed.contains(where: { $0.summary.id == saved.id }) else {
            return fail("an unreadable note stays listed with its raw text and hides nothing else")
        }
        guard try !content.synchronizationDocuments().contains(where: { $0.record.formatIssue != nil }) else {
            return fail("an unreadable note never syncs")
        }
        let rail = CapsuleRailLibrary.load(contentStore: content, passwordStore: CapsulePasswordStore(rootURL: root))
        guard let railNote = try rail.content.get().first(where: { $0.id == saved.id }),
              railNote.headerFields == [CapsuleField(label: "机房", value: "杭州")],
              railNote.preview == "IP 10.0.0.2" else {
            return fail("rail cards show the summary, else the first readable row")
        }

        let passwords = CapsulePasswordStore(rootURL: root)
        let secret = try passwords.put(CapsulePasswordWriteRequest(
            title: "公司邮箱", body: "- 用户名：isaac\n- 密码：hunter22", summaryText: "工作用"
        ))
        guard try passwords.listSummaries().first?.summaryText == "工作用",
              try passwords.record(id: secret.id).summary.summaryText == "工作用",
              !(try String(contentsOf: secret.fileURL, encoding: .utf8)).contains("hunter22") else {
            return fail("password summary is plaintext while the body stays encrypted")
        }
        do {
            _ = try passwords.put(CapsulePasswordWriteRequest(
                id: secret.id, title: "公司邮箱", body: "- 密码：hunter22", summaryText: "hunter22"
            ), expectedRevision: nil)
            return fail("a summary containing the secret was accepted")
        } catch CapsulePasswordStoreError.invalidRequest { }
        let railPasswords = try CapsuleRailLibrary.load(contentStore: content, passwordStore: passwords).passwords.get()
        guard railPasswords.first?.preview == "工作用",
              railPasswords.first?.payload == nil,
              railPasswords.first?.searchText.contains("hunter22") == false else {
            return fail("password cards show only title and summary")
        }
        // The pre-v2 fold format already follows the grammar.
        let legacy = "- 网址：https://mail.example\n- 用户名：isaac\n- 密码：old\n- 曾用密码 1：older"
        guard CapsuleEntryGrammar.fields(body: legacy).map(\.label) == ["网址", "用户名", "密码", "曾用密码 1"],
              CapsuleEntryGrammar.fields(body: legacy).filter(\.isSecret).map(\.label) == ["密码", "曾用密码 1"] else {
            return fail("older password bodies divide into fields unchanged")
        }
    } catch {
        return fail("store operation")
    }

    // Copied secrets clear themselves unless something newer was copied.
    let pasteboard = NSPasteboard(name: .init("RIMES.CapsuleStructureSmoke.\(UUID())"))
    defer { pasteboard.releaseGlobally() }
    guard CapsulePasswordClipboard.write("s3cret", to: pasteboard, clearAfter: 0.05) else {
        return fail("secret copy")
    }
    RunLoop.current.run(until: Date().addingTimeInterval(0.25))
    guard pasteboard.string(forType: .string) == nil else { return fail("secret clears itself") }
    _ = CapsulePasswordClipboard.write("s3cret", to: pasteboard, clearAfter: 0.05)
    pasteboard.clearContents()
    pasteboard.setString("user copy", forType: .string)
    RunLoop.current.run(until: Date().addingTimeInterval(0.25))
    guard pasteboard.string(forType: .string) == "user copy" else {
        return fail("a later copy is never cleared")
    }

    print("PASS: capsule structure grammar, headers, summaries, degraded notes, TOTP, clipboard clearing")
    return true
}

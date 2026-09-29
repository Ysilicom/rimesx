import AppKit
import Foundation

func runCapsuleModuleSmokeTest() -> Bool {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(
        "rimes-capsule-module-smoke-\(UUID().uuidString)", isDirectory: true
    )
    let suite = "rimes-capsule-module-smoke-\(UUID().uuidString)"
    guard let defaults = UserDefaults(suiteName: suite) else { return false }
    defer {
        try? FileManager.default.removeItem(at: root)
        defaults.removePersistentDomain(forName: suite)
    }
    do {
        let content = CapsuleContentStore(rootURL: root)
        let passwords = CapsulePasswordStore(rootURL: root)
        let clipboard = try ClipboardHistoryStore(
            rootDirectory: root.appendingPathComponent("clipboard"))
        let captures = try CaptureStore(
            root: root.appendingPathComponent("captures"))
        let repo = CapsuleWindowRepository(
            contentStore: content, passwordStore: passwords,
            clipboardStore: clipboard, captureStore: captures
        )
        let oldNote = try content.put(CapsuleContentWriteRequest(
            type: .note, title: "旧笔记", content: "plain Markdown"))

        let registry = PluginRegistry(
            internalPlugins: CapsuleModuleID.allCases.map(CapsuleBuiltInPlugin.init),
            defaults: defaults,
            bufferPluginSelection: BufferPluginSelectionStore(defaults: defaults),
            chordExtensionStore: ChordExtensionStore(defaults: defaults)
        )
        guard registry.plugins(capability: .capsuleModule).count == 5,
              registry.plugins(capability: .hostModule).count == 5,
              registry.plugins(capability: .capsuleModule).allSatisfy({
                  $0.descriptor.kind == .capsule
              }),
              CoreSettingsSubpages.descriptors(for: .plugins).contains(where: {
                  $0.id == PluginManagementSubpage.capsulePlugins.id
              }),
              CapsuleModuleID.allCases.allSatisfy({ registry.isEnabled($0.pluginKey) })
        else { return fail("five official modules default enabled") }
        var notifications = 0
        let observer = NotificationCenter.default.addObserver(
            forName: .pluginRegistryDidChange, object: registry, queue: nil
        ) { _ in notifications += 1 }
        defer { NotificationCenter.default.removeObserver(observer) }
        try registry.setEnabled(false, for: CapsuleModuleID.notes.pluginKey)
        try registry.setEnabled(false, for: CapsuleModuleID.notes.pluginKey)
        guard !registry.isEnabled(CapsuleModuleID.notes.pluginKey), notifications == 1,
              try content.record(id: oldNote.id).content == "plain Markdown" else {
            return fail("disable/no-op notification")
        }
        try registry.setEnabled(true, for: CapsuleModuleID.notes.pluginKey)
        guard registry.isEnabled(CapsuleModuleID.notes.pluginKey), notifications == 2 else {
            return fail("module restore")
        }

        let bullet = try repo.save(CapsuleWindowDraft(
            kind: .note, title: "清单", content: "- [ ] first\n  - [x] second",
            noteFormat: .bullet))
        guard try repo.list(module: .notes, filter: .other).contains(where: { $0.id == oldNote.id }),
              try repo.list(module: .notes, filter: .bullet).map(\.id) == [bullet.id],
              CapsuleBulletMarkdown.toggledLine("  - [ ] first") == "  - [x] first",
              CapsuleBulletMarkdown.indentedLine("- [ ] task", outdent: false)
                == "  - [ ] task" else { return fail("note classification/Bullet") }

        let attributed = NSMutableAttributedString(string: "Rich note")
        guard let bitmap = NSBitmapImageRep(
            bitmapDataPlanes: nil, pixelsWide: 2, pixelsHigh: 2,
            bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true,
            isPlanar: false, colorSpaceName: .deviceRGB,
            bytesPerRow: 0, bitsPerPixel: 0
        ) else { return fail("image fixture") }
        bitmap.setColor(.systemBlue, atX: 0, y: 0)
        let image = NSImage(size: NSSize(width: 2, height: 2))
        image.addRepresentation(bitmap)
        let attachment = NSTextAttachment()
        attachment.image = image
        attributed.append(NSAttributedString(attachment: attachment))
        guard let native = attributed.rtfd(
            from: NSRange(location: 0, length: attributed.length),
            documentAttributes: [:]),
              let decoded = NSAttributedString(rtfd: native, documentAttributes: nil),
              decoded.attribute(.attachment, at: 9, effectiveRange: nil) != nil
        else { return fail("RTFD image roundtrip") }
        let rich = try repo.save(CapsuleWindowDraft(
            kind: .note, title: "富文本",
            content: CapsuleRichTextProjection.markdown(from: attributed),
            noteFormat: .richText, richTextData: native))
        guard try repo.list(module: .notes, filter: .richText).map(\.id) == [rich.id],
              try content.record(id: rich.id).richTextData == native,
              try content.synchronizationDocuments().contains(where: { $0.record.summary.id == rich.id })
        else { return fail("rich asset roundtrip and sync scope") }
        guard let syncDocument = try content.synchronizationDocuments()
            .first(where: { $0.record.summary.id == rich.id }) else {
            return fail("rich sync document")
        }
        let received = CapsuleContentStore(rootURL: root.appendingPathComponent("received"))
        let applied = try received.applySynchronizedDocument(
            syncDocument.data, id: rich.id, expectedRevision: nil)
        guard applied.richTextData == native,
              !applied.projectionConflict else {
            return fail("rich asset hash after sync import")
        }
        let markdown = try String(contentsOf: rich.fileURL, encoding: .utf8)
        let edited = markdown.replacingOccurrences(of: "\n\nRich note", with: "\n\nObsidian edit")
        guard edited != markdown else { return fail("projection fixture") }
        try edited.write(to: rich.fileURL, atomically: true, encoding: .utf8)
        guard try content.record(id: rich.id).projectionConflict else {
            return fail("Obsidian projection conflict")
        }
        let changedRow = try repo.list(kind: .note).first { $0.id == rich.id }
        guard let changedRow else { return fail("rich row after external edit") }
        var changedDraft = try repo.draft(for: changedRow)
        do {
            _ = try repo.save(changedDraft)
            return fail("conflicted projection saved silently")
        } catch CapsuleWindowDraftError.projectionConflict {}
        changedDraft.content = "Obsidian edit"
        changedDraft.richTextData = NSAttributedString(string: changedDraft.content)
            .rtfd(from: NSRange(location: 0, length: (changedDraft.content as NSString).length),
                  documentAttributes: [:])
        changedDraft.projectionConflict = false
        _ = try repo.save(changedDraft)
        guard try content.record(id: rich.id).projectionConflict == false else {
            return fail("explicit projection import")
        }

        let project = root.appendingPathComponent("sample-project", isDirectory: true)
        try FileManager.default.createDirectory(at: project,
            withIntermediateDirectories: true)
        let projectRow = try repo.save(CapsuleWindowDraft(
            kind: .resource, title: "工程", content: project.path,
            resourceType: .project))
        guard try repo.list(module: .resources, filter: .project).map(\.id) == [projectRow.id],
              try !content.synchronizationDocuments().contains(where: {
                  $0.record.summary.id == projectRow.id
              }) else { return fail("project path and local sync boundary") }

        let oldPassword = try passwords.put(CapsulePasswordWriteRequest(
            title: "旧密码", body: "old-secret"))
        let login = try passwords.put(CapsulePasswordWriteRequest(
            title: "站点", body: "login-secret", category: .login))
        guard try repo.list(module: .passwords, filter: .other).map(\.id) == [oldPassword.id],
              try repo.list(module: .passwords, filter: .login).map(\.id) == [login.id],
              try !String(contentsOf: login.fileURL, encoding: .utf8).contains("login-secret")
        else { return fail("password classification and masking") }

        let now = Date()
        _ = try clipboard.insertOrPromote(
            kind: .link, displayText: "https://example.org", searchText: "https://example.org",
            canonicalText: "https://example.org", textCompleteness: .complete,
            capturedAt: now, sourceApplicationName: "Browser",
            sourceApplicationBundleIdentifier: nil)
        _ = try clipboard.insertOrPromote(
            kind: .files, displayText: "figure.png", searchText: "figure.png",
            canonicalText: "file:///tmp/figure.png", textCompleteness: .complete,
            capturedAt: now.addingTimeInterval(1), sourceApplicationName: "Finder",
            sourceApplicationBundleIdentifier: nil)
        guard try repo.list(module: .temporary, filter: .text).count == 1,
              try repo.list(module: .temporary, filter: .image).count == 1 else {
            return fail("clipboard text and single image file classification")
        }

        let imageURL = root.appendingPathComponent("capture.png")
        try Data([0x89, 0x50, 0x4e, 0x47]).write(to: imageURL)
        let imported = try captures.importFile(imageURL, kind: .image,
                                               title: "Screenshot", source: "导入")
        _ = try captures.collect(imported.id)
        guard try repo.list(module: .capture, filter: .image).count == 1,
              try captures.prune(now: now.addingTimeInterval(40 * 24 * 3600),
                                 retention: 0, capacity: 0) == 0,
              try captures.record(imported.id).collectionID != nil else {
            return fail("capture collection dedupe and retention")
        }
        print("capsule-module-smoke: OK")
        return true
    } catch {
        return fail("\(error.localizedDescription)")
    }
}

private func fail(_ reason: String) -> Bool {
    FileHandle.standardError.write(Data("capsule-module-smoke: FAIL \(reason)\n".utf8))
    return false
}

import AppKit
import Carbon.HIToolbox

/// Native UI and migration regression fixtures. Never opens a live Capsule,
/// system pasteboard, or iCloud library, and never prints a credential.
@MainActor
enum CapsuleInteractionSmoke {
    static func run(output: URL? = nil) -> Bool {
        NSApp.setActivationPolicy(.accessory)
        NSApp.finishLaunching()
        NSApp.activate(ignoringOtherApps: true)
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("rimes-capsule-interaction-\(UUID())")
        let suite = "RIMES.CapsuleInteractionSmoke.\(UUID())"
        guard let defaults = UserDefaults(suiteName: suite) else { return false }
        defer {
            defaults.removePersistentDomain(forName: suite)
            try? FileManager.default.removeItem(at: root)
        }
        do {
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            if let output { try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true) }
            let passwords = CapsulePasswordStore(rootURL: root)
            let content = CapsuleContentStore(rootURL: root)
            let repository = CapsuleWindowRepository(contentStore: content, passwordStore: passwords)
            let store = CapsuleRevealPasscodeStore(defaults: defaults)
            let code = CapsuleRevealPasscode(chords: ["ab", "df", "jk", "mn"].map { CapsuleRevealChord($0)! })!
            store.set(code)
            let chords: [[UInt16]] = [[0, 11], [2, 3], [38, 40], [46, 45]]
            let secretBody = "- 用户名：demo\n- 密码：fixture-secret-body\n备注一行"
            let note = try repository.save(CapsuleWindowDraft(kind: .note, title: "登录资料", content: secretBody))
            let password = try repository.moveNoteToPassword(note)
            try require(try repository.list(kind: .note).allSatisfy { $0.id != note.id }, "migration removes only the committed source")
            try require(try passwords.record(id: password.id).secret.body == secretBody, "migration preserves content")
            let bytes = try Data(contentsOf: password.fileURL)
            try require(!String(decoding: bytes, as: UTF8.self).contains("fixture-secret-body"), "destination is encrypted")
            let stale = try repository.save(CapsuleWindowDraft(kind: .note, title: "并发修改", content: "initial"))
            var updated = try repository.draft(for: stale)
            updated.content = "updated"
            _ = try repository.save(updated)
            do { _ = try repository.moveNoteToPassword(stale); throw Failure("stale migration accepted") }
            catch CapsuleWindowRepositoryError.staleRecord { }
            try require(try repository.list(kind: .password).count == 1, "stale migration creates no duplicate")
            try require(!CapsuleRailSaveRules.mayRemoveSource(after: []), "empty migration preserves source")
            try require(!CapsuleRailSaveRules.mayRemoveSource(after: [.saved(.pdf, UUID()), .unsupported(.fileType)]), "mixed card preserves source")
            try require(!CapsuleRailSaveRules.mayRemoveSource(after: [.failed]), "failed migration preserves source")
            try require(CapsuleRailSaveRules.mayRemoveSource(after: [.saved(.image, UUID()), .alreadySaved(.pdf, UUID())]), "complete migration may remove source")

            let model = ClipboardHistoryModel(configuration: .init(), pasteboard: EmptyPasteboard(), schedulesAutomaticPolling: false)
            model.start()
            model.update(windowVisible: true, captureEnabled: true, protection: [])
            let entries = try CapsuleRailLibrary.load(contentStore: content, passwordStore: passwords).passwords.get()
            let notes = (1...2).map { index in
                CapsuleRailEntry(id: UUID(), kind: .note, title: "笔记 \(index)", preview: "测试内容",
                    updatedAt: Date(), payload: "## 服务器\n- IP：10.0.0.\(index)\n- 账号：root\n一行说明 \(index)",
                    searchText: "笔记", headerFields: [CapsuleField(label: "机房", value: "杭州")])
            }
            var reads = 0
            let library = CapsuleRailLibrary(loader: { (.success(notes), .success(entries)) }, passwordReader: { entry in
                reads += 1
                return try passwords.record(id: entry.id).secret.body
            })
            let pane = ClipboardHistoryPaneView(model: model, library: library, passcodeStore: store)
            let pasteboard = NSPasteboard(name: .init("RIMES.PasswordCopySmoke.\(UUID())"))
            defer { pasteboard.releaseGlobally() }
            pane.copyPassword = { CapsulePasswordClipboard.write($0, to: pasteboard) }
            let window = NSWindow(contentRect: NSRect(x: 80, y: 100, width: ClipboardHistoryWindowMetrics.preferredWidth, height: ClipboardHistoryWindowMetrics.preferredHeight), styleMask: [.titled], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            defer { window.orderOut(nil) }
            window.contentView = pane
            window.makeKeyAndOrderFront(nil)
            // Command-line smoke runs have no normal NSApplication event
            // loop. Drive AppKit's native key lifecycle explicitly.
            window.becomeKey()
            try require(wait { window.isKeyWindow }, "native fixture window becomes key")
            pane.onRequestPasswordInput = { window.makeKeyAndOrderFront(nil); return window.canBecomeKey }
            pane.selectTab(.saved(.password))
            try require(wait { pane.capsuleRailSnapshotForSmoke().cardCount == 1 }, "password card loads")
            pane.layoutSubtreeIfNeeded()
            pane.hoverCardForPreview(at: 0)
            let lockedHoverCopy = descendants(pane).compactMap { $0 as? NSButton }.first {
                $0.identifier?.rawValue == "capsule-card-copy"
            }!
            try require(lockedHoverCopy.isHidden, "locked password has no hover copy button")
            lockedHoverCopy.performClick(nil)
            try require(!pane.hasPasswordInteraction && reads == 0 && pasteboard.string(forType: .string) == nil,
                        "generic copy cannot authenticate or copy a password")
            try require(pane.activateSelectedItems(), "Return opens the password detail and starts authentication")
            pane.layoutSubtreeIfNeeded()
            try require(reads == 0, "no decryption before authentication")
            guard let lockedDetail = pane.openDetailForSmoke else { throw Failure("password detail opens") }
            try require(lockedDetail.mode == .password && !lockedDetail.isUnlocked
                        && lockedDetail.rowCopyTextsForSmoke.isEmpty,
                        "a locked detail holds no rows")
            let verifier = descendants(pane).compactMap { $0 as? CapsuleInlinePasscodeView }.first!
            try require(verifier.isDescendant(of: lockedDetail), "verification lives in the detail")
            let slots = descendants(verifier).compactMap { $0 as? NSTextField }.filter {
                $0.identifier?.rawValue.hasPrefix("capsule-passcode-slot-") == true
            }
            let emptySlots = ["1", "2", "3", "4"]
            try require(slots.map(\.stringValue) == emptySlots && verifier.capture.feedback == .idle,
                        "verification starts waiting with four empty progress slots")
            try require(descendants(verifier).filter { $0 is CapsuleRevealChordCaptureView }.count == 1
                        && slots.count == 4,
                        "verification shows four masked slots and one native input")
            let back = descendants(lockedDetail).compactMap { $0 as? NSButton }.first {
                $0.identifier?.rawValue == "capsule-detail-back"
            }
            try require(back != nil, "locked password detail has a visible return button")
            if let back {
                let point = lockedDetail.convert(NSPoint(x: back.bounds.midX, y: back.bounds.midY), from: back)
                let hitPoint = lockedDetail.convert(point, to: lockedDetail.superview)
                try require(!back.isHidden && lockedDetail.hitTest(hitPoint) === back,
                            "password return button receives clicks")
            }
            let statusBar = descendants(verifier).first {
                $0.identifier?.rawValue == "capsule-passcode-status-bar"
            }
            try require(statusBar != nil, "verification has a compact keyboard status bar")
            if let statusBar {
                let frame = lockedDetail.convert(statusBar.bounds, from: statusBar)
                let rightCenter = (ClipboardHistoryWindowMetrics.cardWidth + lockedDetail.bounds.maxX) / 2
                try require(frame.width <= 482 && abs(frame.midX - rightCenter) < 20,
                            "passcode module is centered in the right detail pane")
            }
            for view in (slots as [NSView]) + [verifier.capture] {
                try require(verifier.bounds.contains(verifier.convert(view.bounds, from: view)),
                            "password input and all four slots fit inside the detail")
            }
            try require(lockedHoverCopy.isHidden, "no card copy action during verification")
            let hint = descendants(pane).first { $0.identifier?.rawValue == "capsule-rail-hint" }!
            let hintRect = pane.convert(hint.bounds, from: hint)
            let bottomGap = pane.isFlipped ? pane.bounds.maxY - hintRect.maxY : hintRect.minY - pane.bounds.minY
            try require((6...16).contains(bottomGap), "rail has no empty bottom row")
            let plainKey = NSEvent.keyEvent(with: .keyDown, location: .zero, modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, characters: "a", charactersIgnoringModifiers: "a", isARepeat: false, keyCode: 0)!
            let copyKey = NSEvent.keyEvent(with: .keyDown, location: .zero, modifierFlags: .command, timestamp: 0, windowNumber: window.windowNumber, context: nil, characters: "c", charactersIgnoringModifiers: "c", isARepeat: false, keyCode: 8)!
            func key(_ code: Int, _ characters: String = "") -> NSEvent {
                NSEvent.keyEvent(with: .keyDown, location: .zero, modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, characters: characters, charactersIgnoringModifiers: characters, isARepeat: false, keyCode: UInt16(code))!
            }
            try require(!pane.handleStandaloneKeyEquivalent(plainKey), "native password keys are not swallowed by rail shortcuts")
            try require(pane.handleStandaloneKeyEquivalent(copyKey) && pasteboard.string(forType: .string) == nil,
                        "a locked password blocks clipboard shortcuts")
            try render(pane, to: output?.appendingPathComponent("rail-password-detail-locked.png"))
            send([[0], [0], [0], [0]], to: window)
            try require(reads == 0, "wrong code cannot decrypt")
            try require(verifier.capture.feedback == .failure && slots.map(\.stringValue) == emptySlots,
                        "wrong code clears all slots and preserves retry feedback after release")
            try require(verifier.railErrorVisibleForSmoke,
                        "wrong code shows an inline error without a full-width red input")
            try render(pane, to: output?.appendingPathComponent("rail-password-detail-error.png"))
            send([chords[0], [36]], to: window)
            try require(reads == 0 && verifier.capture.feedback == .failure && slots.map(\.stringValue) == emptySlots,
                        "invalid physical key discards the partial attempt")
            for key in chords[0] { sendKey(key, type: .keyDown, to: window) }
            try require(verifier.capture.feedback == .input && slots.map(\.stringValue) == ["●", "2", "3", "4"],
                        "new attempt shows only masked active progress")
            sendKey(chords[0][0], type: .keyUp, to: window)
            try require(verifier.capture.feedback == .input && reads == 0,
                        "partially released chord still waits for all keys")
            sendKey(chords[0][1], type: .keyUp, to: window)
            try require(verifier.capture.feedback == .idle && slots.map(\.stringValue) == ["✓", "2", "3", "4"],
                        "released first group returns to waiting and keeps completed progress")
            for index in 1..<3 {
                send([chords[index]], to: window)
                let expected = (0..<4).map { $0 <= index ? "✓" : String($0 + 1) }
                try require(verifier.capture.feedback == .idle && slots.map(\.stringValue) == expected && reads == 0,
                            "each intermediate group returns to waiting without early verification")
            }
            send([chords[3]], to: window)
            try require(reads == 1, "verified detail decrypts once")
            guard let passwordDetail = pane.openDetailForSmoke, passwordDetail.isUnlocked else {
                throw Failure("verified password opens its rows")
            }
            try require(passwordDetail.rowCopyTextsForSmoke == ["demo", "fixture-secret-body", "备注一行"],
                        "the body divides into a field per row plus its free line")
            try require(passwordDetail.renderedValuesForSmoke == ["demo", "••••••••", "备注一行"],
                        "secret values stay masked after verification")
            try require(!pane.copySelectedItems(), "card copy cannot bypass the row actions")
            try require(pasteboard.string(forType: .string) == nil, "verification never automatically copies")
            _ = pane.handleKeyDown(key(kVK_DownArrow))
            try require(passwordDetail.selectedIndexForSmoke == 1, "arrow keys move between rows")
            try require(pane.handleStandaloneKeyEquivalent(copyKey), "⌘C copies the selected row")
            try require(pasteboard.string(forType: .string) == "fixture-secret-body", "only the selected value is copied")
            try require(passwordDetail.renderedValuesForSmoke[1] == "••••••••", "copying a secret does not show it")
            try require(pasteboard.types?.contains(.init("org.nspasteboard.ConcealedType")) == true,
                        "password copy carries a confidential marker")
            do {
                _ = try ClipboardPasteboardArchive.capture(from: pasteboard)
                throw Failure("password copy must not be archived")
            } catch ClipboardPasteboardArchive.ArchiveError.confidentialContent { }
            _ = pane.handleKeyDown(key(kVK_Space, " "))
            try require(passwordDetail.renderedValuesForSmoke[1] == "fixture-secret-body", "space shows the selected secret")
            try require(entries.allSatisfy { $0.payload == nil && !$0.searchText.contains("fixture-secret-body") }, "projection remains redacted")
            try render(pane, to: output?.appendingPathComponent("password-detail-revealed.png"))
            pane.selectTab(.saved(.note))
            try require(!pane.hasPasswordInteraction && pane.openDetailForSmoke == nil, "tab switch closes the detail")
            try require(passwordDetail.rowCopyTextsForSmoke.isEmpty, "a closed passwordDetail keeps no plaintext rows")
            pasteboard.clearContents()
            pane.layoutSubtreeIfNeeded()
            let menus = descendants(pane).compactMap { $0 as? NSButton }.filter { $0.identifier?.rawValue == "capsule-card-menu" }
            let copies = descendants(pane).compactMap { $0 as? NSButton }.filter { $0.identifier?.rawValue == "capsule-card-copy" }
            try require(menus.count == 2 && menus.filter { !$0.isHidden }.count == 1,
                        "only the selected card shows its menu")
            try require(copies.count == 2 && copies.allSatisfy(\.isHidden), "copy buttons start hidden")
            pane.hoverCardForPreview(at: 1)
            try require(copies.filter { !$0.isHidden }.count == 1 && menus.filter { !$0.isHidden }.count == 1,
                        "hover reveals only copy and does not reveal an unselected menu")
            let hoveredCopy = copies.first { !$0.isHidden }!
            let card = hoveredCopy.superview!
            let copyPoint = NSPoint(x: hoveredCopy.frame.midX, y: hoveredCopy.frame.midY)
            try require(card.hitTest(card.convert(copyPoint, to: card.superview)) === hoveredCopy,
                        "hover copy is hit tested as its own button")
            try require(card.isFlipped ? hoveredCopy.frame.midY > card.bounds.midY : hoveredCopy.frame.midY < card.bounds.midY,
                        "copy is at the bottom of the card")
            var copiedNotes = [UUID]()
            pane.onCopySaved = { copiedNotes.append($0.id); return true }
            let selectedBeforeCopy = pane.capsuleRailSnapshotForSmoke().selectedEntryID
            hoveredCopy.performClick(nil)
            try require(copiedNotes == [notes[1].id] && pane.capsuleRailSnapshotForSmoke().selectedEntryID == selectedBeforeCopy,
                        "hover copy targets that card, not the selection, and preserves selection")
            try render(pane, to: output?.appendingPathComponent("cards-selected-hover.png"))

            // The left filter column shows the current module's filters.
            if CapsuleNavigationPolicy.usesModules {
                try require(pane.filterColumnTitlesForSmoke == ["全部", "Bullet", "富文本", "其他"],
                            "notes show their filters in the left column")
                pane.selectFilter(.bullet)
                try require(pane.capsuleRailSnapshotForSmoke().cardCount == 0
                            && pane.activeFilterForSmoke == .bullet,
                            "a filter narrows the cards to that classification")
                let upKey = NSEvent.keyEvent(with: .keyDown, location: .zero, modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, characters: "", charactersIgnoringModifiers: "", isARepeat: false, keyCode: UInt16(kVK_UpArrow))!
                try require(pane.handleKeyDown(upKey) && pane.activeFilterForSmoke == .all
                            && pane.capsuleRailSnapshotForSmoke().cardCount == 2,
                            "↑ steps back to 全部 and every card returns")
                pane.selectTab(.saved(.password))
                try require(pane.filterColumnTitlesForSmoke == ["全部", "登录", "密钥", "其他"],
                            "passwords show their own filters")
                pane.selectTab(.saved(.note))
            }

            // Notes: a double-click opens the detail; Return still inserts the
            // whole note from the card band.
            var insertedRows = [String]()
            var copiedRows = [String]()
            var activatedNotes = [UUID]()
            pane.onInsertSegment = { insertedRows.append($0); return true }
            pane.onCopySegment = { copiedRows.append($0); return true }
            pane.onActivateSaved = { activatedNotes.append($0.id); return true }
            try require(pane.handleSavedCardInteraction(id: notes[0].id, clickCount: 2), "double-click opens a note")
            guard let noteDetail = pane.openDetailForSmoke, noteDetail.mode == .note else {
                throw Failure("note detail opens")
            }
            try require(activatedNotes.isEmpty, "double-click no longer inserts the whole note")
            try require(noteDetail.rowCopyTextsForSmoke == ["杭州", "10.0.0.1", "root", "一行说明 1"],
                        "header property, fields and line each become a row")
            try require(noteDetail.noticeForSmoke.isEmpty, "an ordinary note has no credential notice")
            _ = pane.handleKeyDown(key(kVK_DownArrow))
            try require(pane.handleKeyDown(copyKey) && copiedRows == ["10.0.0.1"], "⌘C copies only the selected value")
            _ = pane.handleKeyDown(key(kVK_Return, "\r"))
            try require(insertedRows == ["10.0.0.1"], "Return inserts only the selected row")
            try render(pane, to: output?.appendingPathComponent("note-detail.png"))
            let noteBack = descendants(noteDetail).compactMap { $0 as? NSButton }.first {
                $0.identifier?.rawValue == "capsule-detail-back"
            }
            try require(noteBack != nil, "note detail has a visible return button")
            if let noteBack {
                let point = noteDetail.convert(NSPoint(x: noteBack.bounds.midX, y: noteBack.bounds.midY), from: noteBack)
                let hitPoint = noteDetail.convert(point, to: noteDetail.superview)
                try require(!noteBack.isHidden && noteDetail.hitTest(hitPoint) === noteBack,
                            "note return button receives clicks")
            }
            noteBack?.performClick(nil)
            try require(pane.openDetailForSmoke == nil, "return button goes back to the cards")
            try require(pane.handleSavedCardInteraction(id: notes[0].id, clickCount: 2),
                        "note detail reopens after returning")
            _ = pane.handleKeyDown(key(kVK_Escape))
            try require(pane.openDetailForSmoke == nil, "Escape returns to the cards")
            try require(pane.activateSelectedItems() && activatedNotes == [notes[0].id],
                        "Return on a card still inserts the whole note")

            pane.selectTab(.saved(.password))
            try require(pane.activateSelectedItems(), "detail can authenticate again")
            send(chords, to: window)
            try require(pane.openDetailForSmoke?.isUnlocked == true, "re-verified")
            NotificationCenter.default.post(name: .capsuleRevealPasscodeDidChange, object: nil)
            try require(!pane.hasPasswordInteraction, "credential change revokes plaintext")
            try require(pane.activateSelectedItems(), "detail reopens after revocation")
            send(chords, to: window)
            model.update(windowVisible: true, captureEnabled: true, protection: [.secureInput])
            try require(!pane.hasPasswordInteraction, "protection removes plaintext")
            let editor = CapsulePaneViewController(repository: repository, cloudSyncController: nil, revealPasscodeStore: store)
            editor.passwordClipboardWriter = { CapsulePasswordClipboard.write($0, to: pasteboard) }
            // Reuse the active window; no app activation or cross-window focus
            // race can accidentally authenticate a background editor.
            let detail = window
            CapsuleWindowGeometry.install(contentController: editor, in: detail)
            editor.setCompactDetail(true)
            editor.reveal(kind: .password, id: password.id)
            try require(wait { descendants(editor.view).contains { ($0 as? NSTextField)?.stringValue == "登录资料" } }, "existing password loads in compact detail")
            try require(detail.isKeyWindow, "detail keeps native key ownership")
            editor.windowBecameKey()
            send([[0], [0], [0], [0]], to: detail)
            try require(!descendants(editor.view).contains { ($0 as? NSTextView)?.isFieldEditor == false }, "wrong detail passcode stays concealed")
            send(chords, to: detail)
            let revealedEditors = descendants(editor.view).compactMap { $0 as? NSTextView }.filter { !$0.isFieldEditor }
            try require(revealedEditors.count == 1 && revealedEditors.first?.string == secretBody, "detail authenticates and shows the selected body")
            let detailCopy = descendants(editor.view).compactMap { $0 as? NSButton }.first {
                $0.identifier?.rawValue == "capsule-password-detail-copy"
            }!
            detailCopy.performClick(nil)
            try require(pasteboard.string(forType: .string) == secretBody, "detail explicit copy uses its verified body")
            pasteboard.clearContents()
            editor.concealPasswordPlaintext()
            detailCopy.performClick(nil)
            try require(pasteboard.string(forType: .string) == nil, "detached detail button cannot copy after conceal")
            try require(revealedEditors.allSatisfy { $0.string.isEmpty && !$0.isDescendant(of: editor.view) }, "conceal scrubs the detached editor and its undo history")
            editor.beginDraft(kind: .password, title: "演示账号（仅测试）", content: "用户名：demo@example.invalid\n密码：fixture-only-secret")
            try require(descendants(editor.view).contains { ($0 as? NSTextView)?.isEditable == true && ($0 as? NSTextView)?.isFieldEditor == false }, "new password directly editable without challenge")
            try require(!descendants(editor.view).contains { $0 is CapsuleInlinePasscodeView }, "creation needs no old-secret verification")
            try render(editor.view, to: output?.appendingPathComponent("password-create.png"))
            editor.concealPasswordPlaintext()
            try require(!descendants(editor.view).contains { ($0 as? NSTextView)?.isFieldEditor == false }, "new password draft conceals on privacy boundary")
            for size in [CapsuleWindowGeometry.detailContentSize, CapsuleWindowGeometry.detailMinimumSize] {
                detail.setContentSize(size)
                editor.view.layoutSubtreeIfNeeded()
                try require(editor.view.bounds.width <= size.width + 1, "compact detail does not expand window")
                try require(!editor.view.hasAmbiguousLayout, "compact detail has definite layout")
            }
            try render(editor.view, to: output?.appendingPathComponent("password-detail-locked.png"))

            var menuCalls = [String]()
            let menu = CapsuleCardMenu.make([
                ("编辑", "pencil", true, { menuCalls.append("edit") }),
                ("迁移", "arrow.right.square", false, { menuCalls.append("move") }),
                ("删除", "trash", true, { menuCalls.append("delete") }),
            ])
            try require(menu.items.allSatisfy { $0.image != nil }, "every menu action has an icon")
            menu.performActionForItem(at: 0)
            menu.performActionForItem(at: 2)
            try require(menuCalls == ["edit", "delete"] && !menu.items[1].isEnabled, "menu callbacks retained and unsupported moves disabled")
            let captureRecord = CaptureRecord(id: UUID(), title: "测试截图", kind: .image,
                createdAt: Date(), source: "fixture", original: "original.png", output: "original.png",
                project: nil, text: "", width: 100, height: 100, duration: 0, collectionID: nil, incomplete: false)
            let captureCard = CaptureHistoryCard(record: captureRecord,
                file: root.appendingPathComponent("unused.png"), previewFile: root.appendingPathComponent("unused.png"))
            let captureButtons = descendants(captureCard).compactMap { $0 as? NSButton }
            let captureMenu = captureButtons.first { $0.identifier?.rawValue == "capsule-card-menu" }!
            let captureCopy = captureButtons.first { $0.identifier?.rawValue == "capsule-card-copy" }!
            try require(captureMenu.isHidden && captureCopy.isHidden, "capture card starts with actions hidden")
            captureCard.selected = true
            try require(!captureMenu.isHidden && captureCopy.isHidden, "capture selection shows only menu")
            captureCard.selected = false
            captureCard.mouseEntered(with: plainKey)
            try require(captureMenu.isHidden && !captureCopy.isHidden, "capture hover shows only copy")
            var captureCopies = 0
            captureCard.copyAction = { captureCopies += 1 }
            captureCopy.performClick(nil)
            captureCard.mouseExited(with: plainKey)
            try require(captureCopies == 1 && captureCopy.isHidden, "capture copy dispatches once and hides on exit")
            print("capsule-interaction-smoke: PASS")
            return true
        } catch {
            // Only our static assertion text is printable; store errors may
            // contain paths and are deliberately not serialized here.
            print("capsule-interaction-smoke: FAIL \((error as? Failure)?.message ?? "fixture operation")")
            return false
        }
    }

    private struct Failure: Error { let message: String; init(_ message: String) { self.message = message } }
    private static func require(_ condition: @autoclosure () throws -> Bool, _ message: String) throws {
        if try !condition() { throw Failure(message) }
    }
    private static func descendants(_ view: NSView) -> [NSView] { [view] + view.subviews.flatMap(descendants) }
    private static func wait(_ ready: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(3)
        while !ready(), Date() < deadline {
            if let event = NSApp.nextEvent(matching: .any, until: Date().addingTimeInterval(0.01), inMode: .default, dequeue: true) {
                NSApp.sendEvent(event)
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.001))
        }
        return ready()
    }
    private static func send(_ chords: [[UInt16]], to window: NSWindow) {
        for chord in chords {
            for type in [NSEvent.EventType.keyDown, .keyUp] {
                for key in chord {
                    sendKey(key, type: type, to: window)
                }
            }
        }
    }
    private static func sendKey(_ key: UInt16, type: NSEvent.EventType, to window: NSWindow) {
        let event = NSEvent.keyEvent(with: type, location: .zero, modifierFlags: [], timestamp: ProcessInfo.processInfo.systemUptime, windowNumber: window.windowNumber, context: nil, characters: "", charactersIgnoringModifiers: "", isARepeat: false, keyCode: key)!
        window.sendEvent(event)
    }
    private static func render(_ view: NSView, to output: URL?) throws {
        guard let output else { return }
        view.layoutSubtreeIfNeeded()
        view.displayIfNeeded()
        guard let bitmap = view.bitmapImageRepForCachingDisplay(in: view.bounds) else { throw Failure("render allocation") }
        view.cacheDisplay(in: view.bounds, to: bitmap)
        guard let png = bitmap.representation(using: .png, properties: [:]) else { throw Failure("render encoding") }
        try png.write(to: output)
    }
    private final class EmptyPasteboard: ClipboardHistoryPasteboardReading {
        var changeCount: Int { 0 }
        func readPlainText() -> String? { nil }
    }
}

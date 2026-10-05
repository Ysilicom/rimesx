import UIKit
import RimesCore

/// A preview of exactly what the active host exposes, not a claim that it is the
/// whole document. Full boundaries are checked separately before any deletion.
struct HostTextSnapshot: Equatable {
    let target: UUID
    let before: String, selected: String, after: String
    // Never concatenate a possibly shortened selection with disjoint context.
    var text: String { selected.isEmpty ? before + after : selected }
}

@MainActor final class ProxyTextDelivery: TextDelivery {
    private weak var controller: UIInputViewController?
    private let active: () -> Bool
    private var markedTarget: UUID?
    private var markedText = ""
    private var markedProxy: (any UITextDocumentProxy)?
    private var writeDepth = 0
    private enum PendingWrite {
        case insert(String), mark(String), discard, move(Int), delete
    }
    private var pendingWrites: [(UUID, PendingWrite)] = []
    private var commitBarrier: UUID?
    private(set) var isImportingHostText = false
    private var hostContextRevision: UInt64 = 0
    /// UIKit callbacks, not the proxy's optimistic local mutation, acknowledge
    /// that the remote host has supplied its current context.
    func hostContextDidChange() { hostContextRevision &+= 1 }
    var isWriting: Bool { writeDepth > 0 || isImportingHostText }
    var hasPendingWrites: Bool { commitBarrier != nil || !pendingWrites.isEmpty }
    /// Uptime of this keyboard's latest write, so host changes it caused can be told
    /// apart from text the system put there (dictation, paste).
    private(set) var lastWriteTime: TimeInterval = -.infinity
    /// Marks an actual change to the app's text (not a no-op cleanup).
    private func wrote() { lastWriteTime = ProcessInfo.processInfo.systemUptime }
    var hasMarkedText: Bool { markedTarget != nil || !pendingWrites.isEmpty }
    init(controller: UIInputViewController, active: @escaping () -> Bool) {
        self.controller = controller; self.active = active
    }
    private func proxy(for target: UUID) -> UITextDocumentProxy? {
        guard active(), let controller, controller.isViewLoaded, controller.view.window != nil,
              DocumentIdentity.read(controller.textDocumentProxy) == target else { return nil }
        return controller.textDocumentProxy
    }
    private func deferWrite(_ write: PendingWrite, target: UUID) -> Bool {
        guard commitBarrier != nil else { return false }
        pendingWrites.append((target, write)); return true
    }
    /// The remote keyboard bridge can coalesce marked-text updates made in the
    /// same turn. Let the commit/unmark leave that turn before starting another
    /// composition. Subsequent edits retain order and recheck their target.
    private func finishCommitTurn() {
        let token = UUID(); commitBarrier = token
        DispatchQueue.main.async { [weak self] in
            guard let self, self.commitBarrier == token else { return }
            self.commitBarrier = nil
            while self.commitBarrier == nil, !self.pendingWrites.isEmpty {
                let (target, write) = self.pendingWrites.removeFirst()
                guard self.proxy(for: target) != nil else { continue }
                switch write {
                case .insert(let text): _ = self.insert(text, target: target)
                case .mark(let text): _ = self.updateMarkedText(text, target: target)
                case .discard: self.discardMarkedText()
                case .move(let steps): _ = self.moveCaret(by: steps, target: target)
                case .delete: _ = self.deleteBackward(target: target)
                }
            }
        }
    }
    func insert(_ text: String, target: UUID) -> Bool {
        writeDepth += 1; defer { writeDepth -= 1 }
        guard !isImportingHostText, !text.isEmpty, let proxy = proxy(for: target) else { return false }
        if deferWrite(.insert(text), target: target) { return true }
        if markedTarget == target {
            // Commit through the owned marked range. A remote custom text host
            // may ignore the mark/selection when handling insertText, appending
            // the candidate beside the preedit or restoring an earlier caret.
            forgetMarkedText()
            wrote(); proxy.setMarkedText(text, selectedRange: NSRange(location: text.utf16.count, length: 0))
            proxy.unmarkText(); finishCommitTurn()
            return true
        }
        wrote(); proxy.insertText(text)
        return true
    }
    @discardableResult func updateMarkedText(_ text: String, target: UUID) -> Bool {
        writeDepth += 1; defer { writeDepth -= 1 }
        guard !isImportingHostText, let proxy = proxy(for: target) else { return false }
        if commitBarrier != nil {
            if !text.isEmpty || !pendingWrites.isEmpty { _ = deferWrite(.mark(text), target: target) }
            return true
        }
        if markedTarget != nil && markedTarget != target { forgetMarkedText() }
        if text.isEmpty { discardMarkedText(); return true }
        guard markedTarget != target || markedText != text else { return true }
        markedTarget = target; markedText = text; markedProxy = proxy
        wrote(); proxy.setMarkedText(text, selectedRange: NSRange(location: text.utf16.count, length: 0))
        return true
    }
    func discardMarkedText() {
        writeDepth += 1; defer { writeDepth -= 1 }
        if commitBarrier != nil {
            if let target = pendingWrites.last?.0 { _ = deferWrite(.discard, target: target) }
            return
        }
        guard let target = markedTarget else { return }
        guard let ownedProxy = proxy(for: target) else { abandonMarkedText(); return }
        forgetMarkedText()
        wrote(); ownedProxy.setMarkedText("", selectedRange: NSRange(location: 0, length: 0))
        ownedProxy.unmarkText()
    }
    /// Once the host changes selection/document, ownership is gone. Never erase
    /// text at the new caret by trying to clean up an old marked range there.
    func abandonMarkedText() {
        writeDepth += 1; defer { writeDepth -= 1 }
        commitBarrier = nil; pendingWrites.removeAll()
        let target = markedTarget
        let previous = target.flatMap { proxy(for: $0) } ?? markedProxy
        forgetMarkedText()
        // Some hosts retain the old field's marked range after focus moves.
        // End that range through its original proxy only if its identity still
        // matches. Unmarking preserves content and cannot erase a new selection.
        guard active(), let previous, let target, DocumentIdentity.read(previous) == target else { return }
        previous.unmarkText()
    }
    func finishDocumentResetIfNeeded() {
        guard !isWriting else { return }
        writeDepth += 1; defer { writeDepth -= 1 }
        guard active(), let controller, controller.isViewLoaded, controller.view.window != nil,
              DocumentIdentity.read(controller.textDocumentProxy) == nil else { return }
        // UIKit may withhold the new document ID while a refocused field still
        // has a mark from its previous session. Unmarking preserves all text and
        // selections; insertion/deletion still require a verified document ID.
        controller.textDocumentProxy.unmarkText()
    }
    func forgetMarkedText() { markedTarget = nil; markedText = ""; markedProxy = nil }
    /// Steps the host caret by whole characters. Offsets are measured from the
    /// proxy context so an emoji or combined character counts as one step.
    func moveCaret(by steps: Int, target: UUID) -> Bool {
        writeDepth += 1; defer { writeDepth -= 1 }
        guard !isImportingHostText, steps != 0, markedTarget == nil, let proxy = proxy(for: target) else { return false }
        if deferWrite(.move(steps), target: target) { return true }
        wrote()
        for _ in 0..<abs(steps) {
            if steps < 0 {
                guard let last = proxy.documentContextBeforeInput?.last else { break }
                proxy.adjustTextPosition(byCharacterOffset: -String(last).utf16.count)
            } else {
                guard let first = proxy.documentContextAfterInput?.first else { break }
                proxy.adjustTextPosition(byCharacterOffset: String(first).utf16.count)
            }
        }
        return true
    }
    func deleteBackward(target: UUID) -> Bool {
        writeDepth += 1; defer { writeDepth -= 1 }
        guard !isImportingHostText, let proxy = proxy(for: target) else { return false }
        if deferWrite(.delete, target: target) { return true }
        wrote(); proxy.deleteBackward(); return true
    }

    func hostTextSnapshot(target: UUID) -> HostTextSnapshot? {
        guard !hasPendingWrites, markedTarget == nil, let proxy = proxy(for: target) else { return nil }
        let value = HostTextSnapshot(target: target, before: proxy.documentContextBeforeInput ?? "",
                                     selected: proxy.selectedText ?? "", after: proxy.documentContextAfterInput ?? "")
        guard DocumentIdentity.read(proxy) == target else { return nil }
        return value
    }

    func requestHostContext(target: UUID) {
        guard !isImportingHostText, !hasPendingWrites, markedTarget == nil,
              let proxy = proxy(for: target), (proxy.selectedText ?? "").isEmpty else { return }
        writeDepth += 1; defer { writeDepth -= 1 }
        proxy.adjustTextPosition(byCharacterOffset: 0)
    }

    enum HostImportResult { case moved, copied, retained, changed }

    /// Import is explicit and bounded. A selection or a context window that cannot
    /// be proven complete is copied, never deleted. Every native edit is read back;
    /// ignored, delayed or unexpected host responses stop further deletion.
    func importHostText(_ original: HostTextSnapshot, valid: () -> Bool,
                        keep: (String) -> Bool) async -> HostImportResult {
        guard !isImportingHostText, !original.text.isEmpty, valid(),
              hostTextSnapshot(target: original.target) == original else { return .changed }
        isImportingHostText = true
        defer { isImportingHostText = false }
        func live() -> Bool { !Task.isCancelled && valid() && proxy(for: original.target) != nil }
        func read() -> HostTextSnapshot? { live() ? hostTextSnapshot(target: original.target) : nil }
        @discardableResult func move(_ offset: Int) -> UInt64? {
            guard offset != 0, live(), let proxy = proxy(for: original.target) else { return nil }
            let revision = hostContextRevision
            wrote(); proxy.adjustTextPosition(byCharacterOffset: offset)
            return revision
        }
        func observe(_ expected: HostTextSnapshot, previous: HostTextSnapshot, after revision: UInt64?) async -> HostTextSnapshot? {
            // Never accept a read in the command's own turn: UITextDocumentProxy
            // predicts edits locally before the host has actually applied them.
            try? await Task.sleep(nanoseconds: 20_000_000)
            for _ in 0..<30 {
                guard let now = read() else { return nil }
                let acknowledged = revision.map { hostContextRevision > $0 } ?? true
                if acknowledged && (now == expected || now != previous) { return now }
                try? await Task.sleep(nanoseconds: 10_000_000)
            }
            // An unchanged host may have ignored a cursor request. An expected
            // but unacknowledged value may only be a cached prediction.
            return read() == previous ? previous : nil
        }
        func copy() -> HostImportResult {
            guard read() == original, keep(original.text) else { return .changed }
            return .copied
        }
        // The proxy does not say whether selectedText is truncated. Never delete
        // an entire native selection based only on that possibly partial string.
        guard original.selected.isEmpty, original.text.count <= 4000 else { return copy() }
        let start = HostTextSnapshot(target: original.target, before: "", selected: "", after: original.text)
        let startRevision = move(-original.before.utf16.count)
        guard let atStart = await observe(start, previous: original, after: startRevision) else { return .changed }
        if atStart != start {
            if atStart == original { return copy() }
            // Restore only a recognisable result of our own movement. A user
            // edit/selection/focus change must never trigger a blind cursor jump.
            guard atStart.selected.isEmpty, !atStart.after.isEmpty,
                  original.text.hasPrefix(atStart.after) else { return .changed }
            let revision = move(original.before.utf16.count)
            guard await observe(original, previous: atStart, after: revision) == original else { return .changed }
            return copy()
        }
        let end = HostTextSnapshot(target: original.target, before: original.text, selected: "", after: "")
        let endRevision = move(original.text.utf16.count)
        guard let atEnd = await observe(end, previous: start, after: endRevision) else { return .changed }
        if atEnd != end {
            let restore: Int
            if atEnd == start { restore = original.before.utf16.count }
            else {
                guard atEnd.selected.isEmpty, !atEnd.before.isEmpty,
                      original.text.hasSuffix(atEnd.before) else { return .changed }
                restore = -original.after.utf16.count
            }
            let revision = move(restore)
            guard await observe(original, previous: atEnd, after: revision) == original else { return .changed }
            return copy()
        }
        // Store the full source in Buffer before touching any of its characters.
        guard read() == end else { return .changed }
        guard keep(original.text) else {
            let revision = move(-original.after.utf16.count)
            _ = await observe(original, previous: end, after: revision)
            return .changed
        }
        var remaining = original.text
        let deadline = ProcessInfo.processInfo.systemUptime + 3
        while !remaining.isEmpty {
            let before = HostTextSnapshot(target: original.target, before: remaining, selected: "", after: "")
            guard read() == before, ProcessInfo.processInfo.systemUptime < deadline,
                  let proxy = proxy(for: original.target) else { return .retained }
            remaining.removeLast()
            let after = HostTextSnapshot(target: original.target, before: remaining, selected: "", after: "")
            wrote(); proxy.deleteBackward()
            // UIKit does not consistently call textDidChange for our own deletes.
            // Boundary probes above require a host callback; each subsequent
            // character edit still yields and checks the exact remaining context.
            guard await observe(after, previous: before, after: nil) == after else { return .retained }
        }
        return .moved
    }
}

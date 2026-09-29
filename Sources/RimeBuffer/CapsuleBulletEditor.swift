import AppKit
import Foundation

/// Bullet notes remain ordinary Markdown. The format marker in front matter
/// distinguishes them from legacy free-form Markdown notes.
enum CapsuleBulletMarkdown {
    static func toggledLine(_ line: String) -> String? {
        let prefix = line.prefix(while: { $0 == " " || $0 == "\t" })
        let body = line.dropFirst(prefix.count)
        if body.hasPrefix("- [ ] ") {
            return prefix + "- [x] " + body.dropFirst(6)
        }
        if body.hasPrefix("- [x] ") || body.hasPrefix("- [X] ") {
            return prefix + "- [ ] " + body.dropFirst(6)
        }
        return nil
    }

    static func indentedLine(_ line: String, outdent: Bool) -> String {
        if outdent {
            if line.hasPrefix("  ") { return String(line.dropFirst(2)) }
            if line.hasPrefix("\t") { return String(line.dropFirst()) }
            return line
        }
        return "  " + line
    }
}

final class CapsuleBulletTextView: NSTextView {
    override func insertTab(_ sender: Any?) { changeIndent(outdent: false) }
    override func insertBacktab(_ sender: Any?) { changeIndent(outdent: true) }

    private func changeIndent(outdent: Bool) {
        let source = string as NSString
        let selection = selectedRange()
        let lineRange = source.lineRange(for: selection)
        let original = source.substring(with: lineRange)
        let changed = original.split(separator: "\n", omittingEmptySubsequences: false)
            .map { CapsuleBulletMarkdown.indentedLine(String($0), outdent: outdent) }
            .joined(separator: "\n")
        guard original != changed,
              shouldChangeText(in: lineRange, replacementString: changed) else { return }
        textStorage?.replaceCharacters(in: lineRange, with: changed)
        didChangeText()
        setSelectedRange(NSRange(location: lineRange.location + (changed as NSString).length,
                                 length: 0))
    }

    override func mouseDown(with event: NSEvent) {
        let point = convert(event.locationInWindow, from: nil)
        let index = characterIndexForInsertion(at: point)
        let source = string as NSString
        guard source.length > 0 else { super.mouseDown(with: event); return }
        let lineRange = source.lineRange(for: NSRange(location: min(index, source.length - 1),
                                                      length: 0))
        let line = source.substring(with: lineRange)
        let indentation = line.prefix(while: { $0 == " " || $0 == "\t" }).count
        let checkboxStart = lineRange.location + indentation + 2
        guard index >= checkboxStart, index <= checkboxStart + 3,
              let toggled = CapsuleBulletMarkdown.toggledLine(line),
              shouldChangeText(in: lineRange, replacementString: toggled) else {
            super.mouseDown(with: event)
            return
        }
        textStorage?.replaceCharacters(in: lineRange, with: toggled)
        didChangeText()
    }
}

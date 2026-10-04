import UIKit

/// Visual gap after each block, added as kerning so no characters are inserted.
enum BufferBlockStyle {
    static let gap: CGFloat = 10
    /// Kerns the last character of every block but the final one; returns the ranges.
    static func apply(to text: NSMutableAttributedString, ranges: [NSRange]) {
        for range in ranges.dropLast() where range.length > 0 && NSMaxRange(range) <= text.length {
            text.addAttribute(.kern, value: gap, range: NSRange(location: NSMaxRange(range) - 1, length: 1))
        }
    }
    /// Display ranges for consecutive blocks of `text` (UTF-16, no gaps).
    static func ranges(of blocks: [String]) -> [NSRange] {
        var location = 0
        return blocks.map { block in defer { location += block.utf16.count }; return NSRange(location: location, length: block.utf16.count) }
    }
}

/// Presentation only: unconfirmed text never enters BufferSession or plugin input.
struct BufferComposition {
    let text: NSAttributedString
    let markedRange: NSRange
    /// Zero-length caret position; the caret is drawn as an overlay, never as a character.
    let caretRange: NSRange
    /// Displayed range of each delivery block; empty when blocks are not shown.
    let blockRanges: [NSRange]
    /// Block holding the caret.
    let activeBlock: Int?
    init(source: String, cursor: Int, preedit: String, font: UIFont, selection: Range<Int>? = nil, blocks: [String]? = nil, accent: UIColor = .systemTeal) {
        let offset = min(max(0, cursor), source.count)
        let position = source.index(source.startIndex, offsetBy: offset)
        let prefix = String(source[..<position]), suffix = String(source[position...])
        let marked = NSRange(location: prefix.utf16.count, length: preedit.utf16.count)
        markedRange = marked
        caretRange = NSRange(location: NSMaxRange(marked), length: 0)
        let value = NSMutableAttributedString(string: prefix + preedit + suffix,
                                             attributes: [.font: font, .foregroundColor: UIColor.label])
        if marked.length > 0 {
            value.addAttributes([.foregroundColor: accent, .backgroundColor: accent.withAlphaComponent(0.1),
                                 .underlineStyle: NSUnderlineStyle.single.rawValue], range: marked)
        }
        // Source character offsets → displayed UTF-16. Unconfirmed text sits at the
        // cursor and belongs to whatever ends there.
        func display(_ k: Int) -> Int {
            String(source.prefix(max(0, min(k, source.count)))).utf16.count + (k < offset ? 0 : marked.length)
        }
        if let selection, selection.lowerBound < selection.upperBound {
            let start = display(selection.lowerBound), end = display(selection.upperBound)
            if start < end {
                value.addAttribute(.backgroundColor, value: accent.withAlphaComponent(0.28), range: NSRange(location: start, length: end - start))
            }
        }
        var ranges: [NSRange] = [], active: Int?
        if let blocks {
            var start = 0
            for block in blocks where !block.isEmpty {
                let end = start + block.count
                let lower = start == 0 ? 0 : display(start)
                ranges.append(NSRange(location: lower, length: display(end) - lower))
                if active == nil && offset <= end { active = ranges.count - 1 }
                start = end
            }
            if ranges.isEmpty && marked.length > 0 { ranges = [marked]; active = 0 }
            if active == nil, !ranges.isEmpty { active = ranges.count - 1 }
            BufferBlockStyle.apply(to: value, ranges: ranges)
        }
        blockRanges = ranges; activeBlock = active
        text = value
    }
}

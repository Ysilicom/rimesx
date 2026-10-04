import AppKit

enum CapsuleRichTextProjection {
    static func markdown(from attributed: NSAttributedString) -> String {
        guard attributed.length > 0 else { return "" }
        var result = ""
        attributed.enumerateAttributes(
            in: NSRange(location: 0, length: attributed.length)
        ) { attributes, range, _ in
            if attributes[.attachment] != nil {
                result += "[内嵌图片保存在富文本资产中]"
            } else {
                var fragment = attributed.attributedSubstring(from: range).string
                let traits = (attributes[.font] as? NSFont)
                    .map { NSFontManager.shared.traits(of: $0) } ?? []
                if traits.contains(.boldFontMask), !fragment.isEmpty {
                    fragment = "**\(fragment)**"
                }
                if traits.contains(.italicFontMask), !fragment.isEmpty {
                    fragment = "*\(fragment)*"
                }
                if let link = attributes[.link] as? URL {
                    fragment = "[\(fragment)](\(link.absoluteString))"
                }
                result += fragment
            }
        }
        return result
    }
}

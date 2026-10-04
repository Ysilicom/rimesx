import Foundation

/// Nine-key spelling choices constrain Rime's existing pinyin dictionary. They
/// never enumerate letter combinations or replace the engine's word ranking.
public struct NineKeyPinyin {
    public static let groups = ["abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"]
    public let syllables: [String]
    public init(syllables: [String]) {
        self.syllables = Array(Set(syllables.filter { !$0.isEmpty && $0.count <= 8 && $0.utf8.allSatisfy { (97...122).contains($0) } })).sorted()
    }
    public static func digits(for spelling: String) -> String {
        spelling.lowercased().map { letter in
            groups.firstIndex { $0.contains(letter) }.map { String($0 + 2) } ?? String(letter)
        }.joined()
    }
    private func pendingRange(in raw: String) -> Range<String.Index>? {
        guard let start = raw.firstIndex(where: { ("2"..."9").contains($0) }) else { return nil }
        let end = raw[start...].firstIndex(where: { !("2"..."9").contains($0) }) ?? raw.endIndex
        return start..<end
    }
    public func choices(for raw: String) -> [String] {
        guard let range = pendingRange(in: raw) else { return [] }
        let pending = String(raw[range])
        return syllables.filter { pending.hasPrefix(Self.digits(for: $0)) }
            .sorted { a, b in a.count == b.count ? a < b : a.count > b.count }
    }
    public func selecting(_ syllable: String, in raw: String) -> String? {
        guard syllables.contains(syllable), let range = pendingRange(in: raw) else { return nil }
        let digits = Self.digits(for: syllable), pending = String(raw[range])
        guard pending.hasPrefix(digits) else { return nil }
        let remainder = String(pending.dropFirst(digits.count)), suffix = String(raw[range.upperBound...])
        // Do not create two separators when the user already divided this syllable.
        let separator = remainder.isEmpty && suffix.hasPrefix("'") ? "" : "'"
        return String(raw[..<range.lowerBound]) + syllable + separator + remainder + suffix
    }
    public static func backspacing(_ raw: String) -> String {
        guard raw.hasSuffix("'") else { return String(raw.dropLast()) }
        let withoutSeparator = String(raw.dropLast())
        let last = withoutSeparator.split(separator: "'", omittingEmptySubsequences: false).last.map(String.init) ?? ""
        if !last.isEmpty && last.utf8.allSatisfy({ (97...122).contains($0) }) {
            return String(withoutSeparator.dropLast(last.count)) + digits(for: last)
        }
        return withoutSeparator
    }
}

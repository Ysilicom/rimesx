import Foundation

/// Word continuations from `associations.tsv` (build-time, from the bundled
/// dictionary): one `word<TAB>next<TAB>next…` line per word, sorted by UTF-8
/// bytes. Lookup binary-searches the mapped bytes; nothing is expanded in memory
/// beyond one line offset per word.
public struct AssociationIndex {
    private let bytes: [UInt8]
    private let lineStarts: [Int]
    public init(data: Data) {
        bytes = [UInt8](data)
        var starts: [Int] = []
        var start = 0
        for (i, byte) in bytes.enumerated() where byte == 0x0A {
            if i > start { starts.append(start) }
            start = i + 1
        }
        if start < bytes.count { starts.append(start) }
        lineStarts = starts
    }
    public init?(contentsOf url: URL) {
        guard let data = try? Data(contentsOf: url, options: .alwaysMapped) else { return nil }
        self.init(data: data)
    }
    public var count: Int { lineStarts.count }
    private func key(at line: Int) -> ArraySlice<UInt8> {
        let start = lineStarts[line]
        var end = start
        while end < bytes.count, bytes[end] != 0x09, bytes[end] != 0x0A { end += 1 }
        return bytes[start..<end]
    }
    public func continuations(for word: String) -> [String] {
        let target = Array(word.utf8)
        guard !target.isEmpty else { return [] }
        var low = 0, high = lineStarts.count - 1
        while low <= high {
            let mid = (low + high) / 2
            let key = key(at: mid)
            if key.elementsEqual(target) {
                var end = key.endIndex
                while end < bytes.count, bytes[end] != 0x0A { end += 1 }
                let fields = String(decoding: bytes[key.endIndex..<end], as: UTF8.self).split(separator: "\t")
                return fields.map(String.init)
            }
            if key.lexicographicallyPrecedes(target) { low = mid + 1 } else { high = mid - 1 }
        }
        return []
    }
}

/// Next words learned on this device. Bounded so it cannot grow without limit.
public struct AssociationHistory: Codable, Equatable {
    public static let maxWords = 600
    public static let maxNextPerWord = 8
    private var next: [String: [String: Int]] = [:]
    private var lastUsed: [String: Int] = [:]
    private var clock = 0
    public init() {}
    public var isEmpty: Bool { next.isEmpty }
    public mutating func record(previous: String, next word: String) {
        guard Associations.isChinese(previous), Associations.isChinese(word),
              previous.count <= 8, word.count <= 8 else { return }
        clock += 1
        var counts = next[previous] ?? [:]
        counts[word, default: 0] += 1
        if counts.count > Self.maxNextPerWord, let weakest = counts.filter({ $0.key != word }).min(by: { ($0.value, $0.key) < ($1.value, $1.key) })?.key {
            counts.removeValue(forKey: weakest)
        }
        next[previous] = counts; lastUsed[previous] = clock
        if next.count > Self.maxWords, let oldest = lastUsed.min(by: { $0.value < $1.value })?.key {
            next.removeValue(forKey: oldest); lastUsed.removeValue(forKey: oldest)
        }
    }
    public func next(after word: String) -> [String] {
        (next[word] ?? [:]).sorted { ($0.value, $1.key) > ($1.value, $0.key) }.map(\.key)
    }
}

public enum Associations {
    public static func isChinese(_ text: String) -> Bool {
        !text.isEmpty && text.unicodeScalars.allSatisfy { (0x3400...0x9FFF).contains($0.value) || (0xF900...0xFAFF).contains($0.value) }
    }
    /// Suggestions after committed `text`: learned next words first, then
    /// dictionary continuations. Tries the whole commit (up to four characters),
    /// then its last two characters, then its last character.
    public static func suggestions(after text: String, index: AssociationIndex?, history: AssociationHistory, limit: Int = 12) -> [String] {
        let trimmed = String(text.reversed().prefix { isChinese(String($0)) }.reversed())
        guard !trimmed.isEmpty else { return [] }
        var keys: [String] = []
        if trimmed.count <= 4 { keys.append(trimmed) }
        for length in [2, 1] {
            let key = String(trimmed.suffix(length)); if !keys.contains(key) { keys.append(key) }
        }
        for key in keys {
            var result: [String] = []
            for word in history.next(after: key) + (index?.continuations(for: key) ?? []) where !result.contains(word) {
                result.append(word)
            }
            if !result.isEmpty { return Array(result.prefix(limit)) }
        }
        return []
    }
}

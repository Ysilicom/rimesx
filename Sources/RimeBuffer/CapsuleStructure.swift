import CryptoKit
import Foundation

// The structure every Capsule note and password is written in, and how the
// detail view divides it into rows that copy on their own.
//
// Body grammar (notes, and the encrypted body of a password):
//
//     ## 分组              a heading groups the rows below it
//     - 用户名：isaac      a field: copying it copies only "isaac"
//     - 任意一行           a line: copies the line without its list marker
//     普通一行             a line
//     ```                  a fenced block copies as one piece
//
// Notes may also carry fields as extra header keys (Obsidian Properties).
// Nothing here writes files; the stores own persistence.

/// One labelled value. Whether it is a secret, a link or a one-time-code seed
/// is inferred from the label and value, so the text form stays plain.
struct CapsuleField: Equatable {
    let label: String
    let value: String

    var isSecret: Bool { CapsuleSecretLabel.isSecret(label) && !isTOTP }
    var isURL: Bool {
        let lowered = value.lowercased()
        return lowered.hasPrefix("https://") || lowered.hasPrefix("http://")
    }
    var isTOTP: Bool { CapsuleTOTP.parameters(label: label, value: value) != nil }
}

enum CapsuleDetailRow: Equatable {
    case field(CapsuleField)
    case line(String)
    case code(String)

    /// Exactly what a copy of this row puts on the pasteboard.
    var copyText: String {
        switch self {
        case let .field(field): return field.value
        case let .line(text): return text
        case let .code(text): return text
        }
    }
}

struct CapsuleDetailSection: Equatable {
    var heading: String?
    var rows: [CapsuleDetailRow]
}

enum CapsuleEntryGrammar {
    /// Labels longer than this read as sentences, not as field names.
    static let maximumLabelCharacters = 24

    static func sections(body: String,
                         headerFields: [CapsuleField] = []) -> [CapsuleDetailSection] {
        var sections: [CapsuleDetailSection] = []
        var current = CapsuleDetailSection(heading: nil, rows: headerFields.map(CapsuleDetailRow.field))
        var fence: [String]?
        var fenceMarker = ""

        func flush() {
            if current.heading != nil || !current.rows.isEmpty { sections.append(current) }
        }

        for rawLine in body.replacingOccurrences(of: "\r\n", with: "\n").split(
            separator: "\n", omittingEmptySubsequences: false
        ) {
            let line = String(rawLine)
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if var block = fence {
                if trimmed.hasPrefix(fenceMarker), trimmed.allSatisfy({ String($0) == String(fenceMarker.first!) }) {
                    current.rows.append(.code(block.joined(separator: "\n")))
                    fence = nil
                } else {
                    block.append(line)
                    fence = block
                }
                continue
            }
            if trimmed.hasPrefix("```") || trimmed.hasPrefix("~~~") {
                fenceMarker = String(trimmed.prefix(3))
                fence = []
                continue
            }
            guard !trimmed.isEmpty else { continue }
            if let heading = headingText(trimmed) {
                flush()
                current = CapsuleDetailSection(heading: heading, rows: [])
                continue
            }
            let item = listItemText(trimmed)
            if let item, let field = field(item) {
                current.rows.append(.field(field))
            } else {
                let text = item ?? (trimmed.hasPrefix(">") ? String(trimmed.dropFirst()).trimmingCharacters(in: .whitespaces) : trimmed)
                if !text.isEmpty { current.rows.append(.line(text)) }
            }
        }
        if let block = fence, !block.isEmpty {
            current.rows.append(.code(block.joined(separator: "\n")))
        }
        flush()
        return sections
    }

    static func fields(body: String) -> [CapsuleField] {
        sections(body: body).flatMap(\.rows).compactMap {
            if case let .field(field) = $0 { return field }
            return nil
        }
    }

    /// A list item's `标签：值` or `label: value`. The value must be present
    /// and the label short, so an ordinary sentence with a colon stays a line.
    static func field(_ item: String) -> CapsuleField? {
        guard let separator = item.firstIndex(where: { $0 == "：" || $0 == ":" }) else {
            return nil
        }
        let label = item[..<separator].trimmingCharacters(in: .whitespaces)
        let value = item[item.index(after: separator)...].trimmingCharacters(in: .whitespaces)
        guard !label.isEmpty, !value.isEmpty,
              label.count <= maximumLabelCharacters,
              // "https://…" as a bare item is a link, not a field named "https".
              !value.hasPrefix("//") else { return nil }
        return CapsuleField(label: label, value: value)
    }

    /// A new password starts from this, so the fields are written the same
    /// way every time.
    static let passwordTemplate = "- 用户名：\n- 密码：\n- 网址：\n"

    /// Drops `- 标签：` lines left empty (for example from the template).
    static func removingEmptyFields(_ body: String) -> String {
        body.split(separator: "\n", omittingEmptySubsequences: false).filter { line in
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            guard let first = trimmed.first, "-*+".contains(first),
                  trimmed.dropFirst().first == " ",
                  let last = trimmed.last, last == "：" || last == ":" else { return true }
            let label = trimmed.dropFirst(2).dropLast().trimmingCharacters(in: .whitespaces)
            return label.isEmpty || label.count > maximumLabelCharacters
                || label.contains(":") || label.contains("：")
        }.joined(separator: "\n")
    }

    /// The first readable line, for a card that has no written summary.
    static func fallbackSummary(body: String) -> String? {
        for section in sections(body: body) {
            for row in section.rows {
                switch row {
                case let .line(text): return CapsuleSummaryRules.clip(text)
                case let .field(field) where !field.isSecret:
                    return CapsuleSummaryRules.clip("\(field.label) \(field.value)")
                case .field, .code: continue
                }
            }
            if let heading = section.heading { return CapsuleSummaryRules.clip(heading) }
        }
        return nil
    }

    /// Whether a note looks like it holds a credential, so the detail view can
    /// suggest moving it into a password entry.
    static func mayContainSecret(body: String, headerFields: [CapsuleField] = []) -> Bool {
        if headerFields.contains(where: \.isSecret) { return true }
        for row in sections(body: body).flatMap(\.rows) {
            switch row {
            case let .field(field) where field.isSecret: return true
            case let .line(text) where CapsuleSecretLabel.mentionsSecret(text): return true
            default: continue
            }
        }
        return false
    }

    private static func headingText(_ trimmed: String) -> String? {
        let hashes = trimmed.prefix { $0 == "#" }
        guard (1...6).contains(hashes.count) else { return nil }
        let rest = trimmed.dropFirst(hashes.count)
        guard rest.first == " " else { return nil }
        let text = rest.trimmingCharacters(in: .whitespaces)
        return text.isEmpty ? nil : text
    }

    /// The text of a `-`, `*`, `+` or `1.` list item, without its marker or a
    /// task checkbox; nil when the line is not a list item.
    private static func listItemText(_ trimmed: String) -> String? {
        var rest: Substring
        if let first = trimmed.first, "-*+".contains(first),
           trimmed.dropFirst().first == " " {
            rest = trimmed.dropFirst(2)
        } else {
            let digits = trimmed.prefix(while: \.isNumber)
            guard !digits.isEmpty, digits.count <= 3 else { return nil }
            let afterDigits = trimmed.dropFirst(digits.count)
            guard let mark = afterDigits.first, mark == "." || mark == ")",
                  afterDigits.dropFirst().first == " " else { return nil }
            rest = afterDigits.dropFirst(2)
        }
        for box in ["[ ] ", "[x] ", "[X] "] where rest.hasPrefix(box) {
            rest = rest.dropFirst(box.count)
        }
        return rest.trimmingCharacters(in: .whitespaces)
    }
}

/// Labels whose values are secrets. English words match whole words so that,
/// for example, "keyboard" is not taken for "key".
enum CapsuleSecretLabel {
    private static let phrases = [
        "密码", "口令", "密钥", "私钥", "秘钥", "助记词", "恢复短语", "恢复码", "安全码",
        "api key", "apikey", "access key", "secret key", "private key",
    ]
    private static let words: Set<String> = [
        "password", "passwd", "pwd", "pass", "passcode", "pin", "secret",
        "token", "key", "cvv", "cvc", "seed", "mnemonic",
    ]

    static func isSecret(_ label: String) -> Bool {
        let lowered = label.lowercased()
        if phrases.contains(where: lowered.contains) { return true }
        let tokens = lowered.split { !$0.isLetter && !$0.isNumber }.map(String.init)
        return tokens.contains(where: words.contains)
    }

    /// Looser, for free text: "账号 root 密码 password" is still a credential.
    static func mentionsSecret(_ text: String) -> Bool {
        let lowered = text.lowercased()
        return ["密码", "口令", "私钥", "助记词", "password", "passwd", "api key", "apikey", "token"]
            .contains(where: lowered.contains)
    }
}

enum CapsuleSummaryRules {
    static let maximumCharacters = 120

    /// One line, trimmed and bounded; nil when nothing is left.
    static func normalized(_ summary: String?) -> String? {
        guard let summary else { return nil }
        let line = summary.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        guard !line.isEmpty else { return nil }
        return String(line.prefix(maximumCharacters))
    }

    static func clip(_ text: String) -> String {
        let line = text.trimmingCharacters(in: .whitespaces)
        guard line.count > maximumCharacters else { return line }
        return String(line.prefix(maximumCharacters - 1)) + "…"
    }

    /// A password summary is shown before verification, so it must not hold
    /// any secret value from the encrypted body.
    static func leaksSecret(summary: String, body: String) -> Bool {
        let lowered = summary.lowercased()
        return CapsuleEntryGrammar.fields(body: body).contains { field in
            (field.isSecret || field.isTOTP) && field.value.count >= 3
                && lowered.contains(field.value.lowercased())
        }
    }
}

/// A deliberately small YAML reader for Capsule headers. It accepts what
/// RIMES writes and what Obsidian's Properties editor writes — including
/// multi-line lists — and keeps every entry's original lines so a save can put
/// back keys RIMES does not own.
struct CapsuleFrontMatter: Equatable {
    struct Entry: Equatable {
        /// Empty for a blank or comment line kept only for round-tripping.
        let key: String
        let lines: [String]
        /// The decoded value of a one-line scalar; nil for lists and blocks.
        let scalar: String?
    }

    enum ParseError: Error { case malformed }

    /// Keys RIMES owns. Obsidian's own metadata is kept but never shown as a
    /// field.
    static let reservedKeys: Set<String> = [
        "capsule", "version", "id", "title", "updated_at", "summary",
        "sync_asset", "sync_project",
        "capsule_note_format", "capsule_resource_type", "capsule_rich_version",
        "capsule_rich_hash", "capsule_projection_hash", "capsule_rich_asset",
    ]
    static let obsidianMetadataKeys: Set<String> = ["tags", "tag", "aliases", "alias", "cssclasses", "cssclass"]

    let entries: [Entry]

    static func parse<Lines: Collection>(_ lines: Lines) throws -> CapsuleFrontMatter
        where Lines.Element == String {
        var entries: [Entry] = []
        var seen = Set<String>()
        var pending: (key: String, lines: [String], value: String)?

        func finish() {
            guard let open = pending else { return }
            let scalar = open.lines.count == 1 ? decodeScalar(open.value) : nil
            entries.append(Entry(key: open.key, lines: open.lines, scalar: scalar))
            pending = nil
        }

        for line in lines {
            if line.first == " " || line.first == "\t" || line.hasPrefix("- ") || line == "-" {
                guard pending != nil else { throw ParseError.malformed }
                pending!.lines.append(line)
                continue
            }
            finish()
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty || trimmed.hasPrefix("#") {
                entries.append(Entry(key: "", lines: [line], scalar: nil))
                continue
            }
            guard let (key, value) = splitKey(line), !key.isEmpty,
                  seen.insert(key).inserted else {
                throw ParseError.malformed
            }
            pending = (key, [line], value)
        }
        finish()
        return CapsuleFrontMatter(entries: entries)
    }

    func entry(_ key: String) -> Entry? { entries.first { $0.key == key } }
    func scalar(_ key: String) -> String? { entry(key)?.scalar }

    /// One-line user properties, in file order, shown as fields.
    var userFields: [CapsuleField] {
        entries.compactMap { entry in
            guard !entry.key.isEmpty,
                  !Self.reservedKeys.contains(entry.key),
                  !Self.obsidianMetadataKeys.contains(entry.key.lowercased()),
                  let value = entry.scalar, !value.isEmpty else { return nil }
            return CapsuleField(label: entry.key, value: value)
        }
    }

    /// Every line RIMES does not own, to be written back unchanged on save.
    var preservedLines: [String] {
        entries.filter { !$0.key.isEmpty && !Self.reservedKeys.contains($0.key) }
            .flatMap(\.lines)
    }

    /// A key ends at the first colon followed by a space or the line end.
    private static func splitKey(_ line: String) -> (String, String)? {
        if line.first == "\"" || line.first == "'" {
            let quote = line.first!
            guard let close = line.dropFirst().firstIndex(of: quote) else { return nil }
            let after = line[line.index(after: close)...]
            guard after.first == ":" else { return nil }
            let key = String(line[line.index(after: line.startIndex)..<close])
            return (key, after.dropFirst().trimmingCharacters(in: .whitespaces))
        }
        var index = line.startIndex
        while index < line.endIndex {
            if line[index] == ":" {
                let next = line.index(after: index)
                if next == line.endIndex || line[next] == " " || line[next] == "\t" {
                    let key = line[..<index].trimmingCharacters(in: .whitespaces)
                    let value = line[next...].trimmingCharacters(in: .whitespaces)
                    return (key, value)
                }
            }
            index = line.index(after: index)
        }
        return nil
    }

    /// JSON-quoted (what RIMES writes), single-quoted, or plain YAML scalars.
    /// Flow collections, anchors and block indicators are not scalars.
    static func decodeScalar(_ raw: String) -> String? {
        if raw.isEmpty { return "" }
        if raw.first == "\"" {
            guard let data = raw.data(using: .utf8),
                  let decoded = try? JSONDecoder().decode(String.self, from: data) else {
                return nil
            }
            return decoded
        }
        if raw.first == "'" {
            guard raw.count >= 2, raw.last == "'" else { return nil }
            return String(raw.dropFirst().dropLast()).replacingOccurrences(of: "''", with: "'")
        }
        if let first = raw.first, "[{|>&*!%@`".contains(first) { return nil }
        if let comment = raw.range(of: " #") {
            return raw[..<comment.lowerBound].trimmingCharacters(in: .whitespaces)
        }
        return raw
    }

    /// The form RIMES writes: always a JSON string, which YAML reads as a
    /// double-quoted scalar.
    static func encodeScalar(_ value: String) -> String {
        let data = try? JSONEncoder().encode(value)
        return data.flatMap { String(data: $0, encoding: .utf8) } ?? "\"\""
    }
}

/// RFC 6238 one-time codes from an `otpauth://totp/…` URI, or from a bare
/// base32 seed under a label such as "TOTP" or "两步验证".
enum CapsuleTOTP {
    struct Parameters: Equatable {
        let secret: Data
        let digits: Int
        let period: Int
    }

    private static let seedLabels = ["totp", "2fa", "otp", "mfa", "两步验证", "二次验证", "动态码", "验证器"]

    static func parameters(label: String, value: String) -> Parameters? {
        let trimmed = value.trimmingCharacters(in: .whitespaces)
        if trimmed.lowercased().hasPrefix("otpauth://totp/"),
           let components = URLComponents(string: trimmed) {
            let items = Dictionary(
                (components.queryItems ?? []).map { ($0.name.lowercased(), $0.value ?? "") },
                uniquingKeysWith: { first, _ in first }
            )
            if let algorithm = items["algorithm"], algorithm.uppercased() != "SHA1" { return nil }
            guard let seed = items["secret"].flatMap(base32Decode) else { return nil }
            let digits = Int(items["digits"] ?? "") ?? 6
            let period = Int(items["period"] ?? "") ?? 30
            guard (6...8).contains(digits), (10...120).contains(period) else { return nil }
            return Parameters(secret: seed, digits: digits, period: period)
        }
        let lowered = label.lowercased()
        guard seedLabels.contains(where: lowered.contains),
              let seed = base32Decode(trimmed), seed.count >= 10 else { return nil }
        return Parameters(secret: seed, digits: 6, period: 30)
    }

    static func code(_ parameters: Parameters, at date: Date) -> (code: String, remaining: Int) {
        let seconds = Int(date.timeIntervalSince1970)
        var counter = UInt64(seconds / parameters.period).bigEndian
        let message = withUnsafeBytes(of: &counter) { Data($0) }
        let mac = Data(HMAC<Insecure.SHA1>.authenticationCode(
            for: message,
            using: SymmetricKey(data: parameters.secret)
        ))
        let offset = Int(mac[mac.count - 1] & 0x0f)
        let binary = (UInt32(mac[offset] & 0x7f) << 24)
            | (UInt32(mac[offset + 1]) << 16)
            | (UInt32(mac[offset + 2]) << 8)
            | UInt32(mac[offset + 3])
        var modulus: UInt32 = 1
        for _ in 0..<parameters.digits { modulus *= 10 }
        let value = String(binary % modulus)
        let code = String(repeating: "0", count: parameters.digits - value.count) + value
        return (code, parameters.period - seconds % parameters.period)
    }

    static func base32Decode(_ text: String) -> Data? {
        let alphabet = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")
        let cleaned = text.uppercased().filter { $0 != " " && $0 != "-" && $0 != "=" }
        guard !cleaned.isEmpty else { return nil }
        var bits: UInt32 = 0
        var bitCount = 0
        var bytes: [UInt8] = []
        for character in cleaned {
            guard let index = alphabet.firstIndex(of: character) else { return nil }
            bits = (bits << 5) | UInt32(index)
            bitCount += 5
            if bitCount >= 8 {
                bitCount -= 8
                bytes.append(UInt8((bits >> UInt32(bitCount)) & 0xff))
            }
        }
        return bytes.isEmpty ? nil : Data(bytes)
    }
}

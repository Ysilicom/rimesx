import Foundation

/// Actions supported by the ordinary typing keyboard. Chord layouts remain separate.
public enum CustomKeyAction: String, Codable, CaseIterable, Sendable {
    case character, space, backspace, enter, shift, numbers, language, emoji, buffer
}

public struct CustomKeyboardKey: Codable, Equatable, Identifiable, Sendable {
    public var id: String
    public var action: CustomKeyAction
    public var text: String
    public var width: Double

    public init(id: String = UUID().uuidString, action: CustomKeyAction = .character,
                text: String = "", width: Double = 1) {
        self.id = id; self.action = action; self.text = text; self.width = width
    }

    public var label: String {
        switch action {
        case .character: return text.isEmpty ? "＋" : text.uppercased()
        case .space: return "空格"
        case .backspace: return "⌫"
        case .enter: return "换行"
        case .shift: return "⇧"
        case .numbers: return "123"
        case .language: return "中 / 英"
        case .emoji: return "☺"
        case .buffer: return "Buffer"
        }
    }

    private enum CodingKeys: String, CodingKey { case id, action, text, width }
    public init(from decoder: Decoder) throws {
        try rejectUnknownFields(decoder, allowed: ["id", "action", "text", "width"])
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(String.self, forKey: .id)
        action = try values.decode(CustomKeyAction.self, forKey: .action)
        text = try values.decode(String.self, forKey: .text)
        width = try values.decode(Double.self, forKey: .width)
    }
}

public struct CustomKeyboardMetrics: Codable, Equatable, Sendable {
    public var keyHeight: Double
    public var gap: Double
    public var padding: Double
    public var splitGap: Double
    public var split: Bool
    public var stagger: Bool

    public init(keyHeight: Double = 46, gap: Double = 6, padding: Double = 6,
                splitGap: Double = 14, split: Bool = false, stagger: Bool = true) {
        self.keyHeight = keyHeight; self.gap = gap; self.padding = padding
        self.splitGap = splitGap; self.split = split; self.stagger = stagger
    }

    private enum CodingKeys: String, CodingKey { case keyHeight, gap, padding, splitGap, split, stagger }
    public init(from decoder: Decoder) throws {
        try rejectUnknownFields(decoder, allowed: ["keyHeight", "gap", "padding", "splitGap", "split", "stagger"])
        let values = try decoder.container(keyedBy: CodingKeys.self)
        keyHeight = try values.decode(Double.self, forKey: .keyHeight)
        gap = try values.decode(Double.self, forKey: .gap)
        padding = try values.decode(Double.self, forKey: .padding)
        splitGap = try values.decode(Double.self, forKey: .splitGap)
        split = try values.decode(Bool.self, forKey: .split)
        stagger = try values.decode(Bool.self, forKey: .stagger)
    }
}

public struct CustomKeyboardKeyFrame: Equatable, Sendable {
    public var key: CustomKeyboardKey
    public var x: Double
    public var y: Double
    public var width: Double
    public var height: Double
}

public struct CustomKeyboardGeometry: Equatable, Sendable {
    public var frames: [CustomKeyboardKeyFrame]
    public var height: Double
}

public enum CustomKeyboardLayoutError: LocalizedError, Equatable {
    case invalid(String)
    public var errorDescription: String? {
        switch self { case .invalid(let message): return message }
    }
}

public struct CustomKeyboardLayout: Codable, Equatable, Identifiable, Sendable {
    public var formatVersion: Int
    public var id: String
    public var name: String
    /// Every row, including function keys, is explicit. The system globe is outside this model.
    public var rows: [[CustomKeyboardKey]]
    public var metrics: CustomKeyboardMetrics

    public init(formatVersion: Int = 1, id: String = UUID().uuidString, name: String,
                rows: [[CustomKeyboardKey]], metrics: CustomKeyboardMetrics = .init()) {
        self.formatVersion = formatVersion; self.id = id; self.name = name
        self.rows = rows; self.metrics = metrics
    }

    private enum CodingKeys: String, CodingKey { case formatVersion, id, name, rows, metrics }
    public init(from decoder: Decoder) throws {
        try rejectUnknownFields(decoder, allowed: ["formatVersion", "id", "name", "rows", "metrics"])
        let values = try decoder.container(keyedBy: CodingKeys.self)
        formatVersion = try values.decode(Int.self, forKey: .formatVersion)
        id = try values.decode(String.self, forKey: .id)
        name = try values.decode(String.self, forKey: .name)
        rows = try values.decode([[CustomKeyboardKey]].self, forKey: .rows)
        metrics = try values.decode(CustomKeyboardMetrics.self, forKey: .metrics)
    }

    public static var templates: [Self] {
        [("qwerty", "26 键 · 经典", false, true),
         ("orthogonal", "26 键 · 正交", false, false),
         ("split", "26 键 · 分体", true, false)].map { id, name, split, stagger in
            var rows = ["qwertyuiop", "asdfghjkl", "zxcvbnm"].map { row in
                row.map { CustomKeyboardKey(id: "\(id).\($0)", text: String($0)) }
            }
            rows.append([CustomKeyAction.numbers, .shift, .buffer, .language, .space, .backspace, .enter].map {
                CustomKeyboardKey(id: "\(id).\($0.rawValue)", action: $0, width: $0 == .space ? 2.8 : 1)
            })
            return Self(id: "rimes.\(id)", name: name, rows: rows,
                        metrics: .init(split: split, stagger: stagger))
        }
    }

    /// Validation is intentionally separate from decoding so incomplete editor drafts can exist.
    public func validated() throws -> Self {
        guard formatVersion == 1 else { throw invalid("暂不支持布局格式版本 \(formatVersion)。") }
        guard !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, id.count <= 128 else {
            throw invalid("布局标识不能为空，且不能超过 128 个字符。")
        }
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, name.count <= 60 else {
            throw invalid("布局名称须为 1–60 个字符。")
        }
        for (name, value, range) in [("键高", metrics.keyHeight, 30.0...72.0),
                                    ("键间距", metrics.gap, 0.0...12.0),
                                    ("边距", metrics.padding, 0.0...28.0),
                                    ("分区间距", metrics.splitGap, 0.0...60.0)] {
            guard value.isFinite, range.contains(value) else {
                throw invalid("\(name)须为 \(Int(range.lowerBound))–\(Int(range.upperBound)) pt。")
            }
        }
        guard (1...6).contains(rows.count), rows.allSatisfy({ (1...16).contains($0.count) }),
              rows.flatMap({ $0 }).count <= 80 else {
            throw invalid("布局须有 1–6 行，每行 1–16 键，总数不超过 80 键。")
        }
        var identifiers = Set<String>(), characters = [String: Int](), functions = Set<CustomKeyAction>()
        for key in rows.flatMap({ $0 }) {
            guard !key.id.isEmpty, key.id.count <= 128, identifiers.insert(key.id).inserted else {
                throw invalid("每个键位须有独立且非空的标识。")
            }
            guard key.width.isFinite, (0.5...4).contains(key.width) else {
                throw invalid("单键宽度比例须为 0.5–4。")
            }
            if key.action == .character {
                guard !key.text.isEmpty else { throw invalid("仍有空白槽位，请分配键位后再应用。") }
                guard key.text.count == 1 else {
                    throw invalid("暂不支持一个键包含多个字母；共键布局需要配套的输入解码方案。")
                }
                guard !key.text.unicodeScalars.contains(where: {
                    CharacterSet.whitespacesAndNewlines.contains($0) || CharacterSet.controlCharacters.contains($0)
                }) else { throw invalid("字符键不能包含空白或控制字符，请使用对应功能键。") }
                guard !"ABCDEFGHIJKLMNOPQRSTUVWXYZ".contains(key.text) else {
                    throw invalid("字母键请使用小写 a–z；大写输入由 Shift 键控制。")
                }
                characters[key.text, default: 0] += 1
            } else {
                guard key.text.isEmpty else { throw invalid("功能键不能附带字符输入。") }
                guard functions.insert(key.action).inserted else { throw invalid("功能键“\(key.label)”不能重复。") }
            }
        }
        let missing = "abcdefghijklmnopqrstuvwxyz".map(String.init).filter { characters[$0] == nil }
        guard missing.isEmpty else { throw invalid("缺少字母键：\(missing.joined(separator: " "))。") }
        let duplicate = "abcdefghijklmnopqrstuvwxyz".map(String.init).filter { (characters[$0] ?? 0) > 1 }
        guard duplicate.isEmpty else { throw invalid("字母键不能重复：\(duplicate.joined(separator: " "))。") }
        for action in [CustomKeyAction.space, .backspace, .enter, .numbers, .language] where !functions.contains(action) {
            throw invalid("缺少必要功能键：\(CustomKeyboardKey(action: action).label)。")
        }
        // A 320 pt phone leaves 310 pt for the key surface after its outer gutters.
        guard geometry(width: 310).frames.allSatisfy({ $0.width >= 12 }) else {
            throw invalid("部分键位在窄屏上过窄，请减少槽位或间距，或调整单键宽度比例（最小 12 pt）。")
        }
        return self
    }

    /// Preview and the extension use identical frames. Invalid draft dimensions are bounded here;
    /// persistence still requires validated(), so corrections are never silently saved.
    public func geometry(width: Double, landscape: Bool = false) -> CustomKeyboardGeometry {
        guard width.isFinite, width > 0, !rows.isEmpty else { return .init(frames: [], height: 0) }
        func bounded(_ value: Double, _ limits: ClosedRange<Double>, fallback: Double) -> Double {
            value.isFinite ? min(limits.upperBound, max(limits.lowerBound, value)) : fallback
        }
        let padding = min(bounded(metrics.padding, 0...28, fallback: 6), width / 4)
        let available = width - padding * 2
        let count = max(1, rows.map(\.count).max() ?? 1)
        let gap = min(bounded(metrics.gap, 0...12, fallback: 6), available / Double(count * 2))
        let splitGap = min(bounded(metrics.splitGap, 0...60, fallback: 14), available / 3)
        let height = bounded(metrics.keyHeight, 30...72, fallback: 46) * (landscape ? 0.78 : 1)
        func weight(_ key: CustomKeyboardKey) -> Double { bounded(key.width, 0.5...4, fallback: 1) }
        func isFunctionRow(_ row: [CustomKeyboardKey]) -> Bool { row.allSatisfy { $0.action != .character } }
        func halves(_ row: [CustomKeyboardKey]) -> [[CustomKeyboardKey]] {
            guard metrics.split, row.count > 1 else { return [row] }
            let pivot = (row.count + 1) / 2
            return [Array(row[..<pivot]), Array(row[pivot...])]
        }
        let characterRows = rows.filter { !$0.isEmpty && !isFunctionRow($0) }
        let unit = characterRows.flatMap { row in
            halves(row).map { half -> Double in
                let groupWidth = metrics.split && row.count > 1 ? (available - splitGap) / 2 : available
                return max(0, groupWidth - Double(max(0, half.count - 1)) * gap) / half.reduce(0) { $0 + weight($1) }
            }
        }.min() ?? 1
        var frames = [CustomKeyboardKeyFrame]()
        for (index, row) in rows.enumerated() {
            guard !row.isEmpty else { continue }
            let functionRow = isFunctionRow(row)
            let groups = functionRow ? [row] : halves(row)
            for (side, group) in groups.enumerated() {
                let groupWidth = groups.count == 2 ? (available - splitGap) / 2 : available
                let groupUnit = functionRow
                    ? max(0, available - Double(row.count - 1) * gap) / row.reduce(0) { $0 + weight($1) }
                    : unit
                let occupied = group.reduce(0) { $0 + weight($1) * groupUnit } + Double(max(0, group.count - 1)) * gap
                let inset = !functionRow && metrics.stagger ? max(0, groupWidth - occupied) / 2 : 0
                var x = padding + Double(side) * (groupWidth + splitGap) + inset
                for key in group {
                    let keyWidth = weight(key) * groupUnit
                    frames.append(.init(key: key, x: x, y: padding + Double(index) * (height + gap),
                                        width: keyWidth, height: height))
                    x += keyWidth + gap
                }
            }
        }
        return .init(frames: frames, height: padding * 2 + Double(rows.count) * height + Double(rows.count - 1) * gap)
    }

    public static func importData(_ data: Data) throws -> Self {
        guard data.count <= 262_144 else { throw invalid("布局文件超过 256 KB，请只导入键位配置 JSON。") }
        do {
            let header = try JSONDecoder().decode(ImportHeader.self, from: data)
            if header.formatVersion != nil { return try JSONDecoder().decode(Self.self, from: data).validated() }
            guard header.version != nil else { throw invalid("这不是 RIMES 键位布局 JSON，输入方案压缩包须使用方案导入。") }
            return try JSONDecoder().decode(PrototypeLayout.self, from: data).converted().validated()
        } catch let error as CustomKeyboardLayoutError { throw error }
        catch { throw invalid("布局 JSON 格式不正确或含有不支持的字段：\(error.localizedDescription)") }
    }

    public func exportData() throws -> Data {
        let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return try encoder.encode(validated())
    }
}

private func invalid(_ message: String) -> CustomKeyboardLayoutError { .invalid(message) }

private struct FieldName: CodingKey {
    let stringValue: String
    let intValue: Int? = nil
    init?(stringValue: String) { self.stringValue = stringValue }
    init?(intValue: Int) { return nil }
}

private func rejectUnknownFields(_ decoder: Decoder, allowed: Set<String>) throws {
    let container = try decoder.container(keyedBy: FieldName.self)
    let unknown = container.allKeys.map(\.stringValue).filter { !allowed.contains($0) }.sorted()
    guard unknown.isEmpty else { throw invalid("布局含有不支持的字段：\(unknown.joined(separator: "、"))。") }
}

private struct ImportHeader: Decodable { var formatVersion: Int?; var version: Int? }

private struct PrototypeLayout: Decodable {
    var name: String
    var rows: [[CustomKeyboardKey]]
    var metrics: CustomKeyboardMetrics

    private enum CodingKeys: String, CodingKey {
        case version, name, template, grouped, rows, functions, settings
        case prototypeOnly, exportedAt, coordinateUnit, previewWidth
    }
    init(from decoder: Decoder) throws {
        try rejectUnknownFields(decoder, allowed: ["version", "name", "template", "grouped", "rows", "functions",
                                                  "settings", "prototypeOnly", "exportedAt", "coordinateUnit", "previewWidth"])
        let values = try decoder.container(keyedBy: CodingKeys.self)
        let version = try values.decode(Int.self, forKey: .version)
        guard version == 1 else { throw invalid("暂不支持原型布局格式版本 \(version)。") }
        guard try !values.decode(Bool.self, forKey: .grouped) else {
            throw invalid("该文件是共键布局预览；9／14／18 键尚需配套解码，暂不能应用。")
        }
        name = try values.decode(String.self, forKey: .name)
        _ = try values.decode(String.self, forKey: .template)
        _ = try values.decodeIfPresent(Bool.self, forKey: .prototypeOnly)
        _ = try values.decodeIfPresent(String.self, forKey: .exportedAt)
        if let unit = try values.decodeIfPresent(String.self, forKey: .coordinateUnit), unit != "pt" {
            throw invalid("布局的坐标单位须为 pt。")
        }
        if let width = try values.decodeIfPresent(Double.self, forKey: .previewWidth), !width.isFinite || width <= 0 {
            throw invalid("布局预览宽度无效。")
        }
        rows = try values.decode([[PrototypeSlot]].self, forKey: .rows).map { try $0.map { try $0.converted() } }
        let functions = try values.decode([PrototypeSlot].self, forKey: .functions)
        if !functions.isEmpty { rows.append(try functions.map { try $0.converted() }) }
        metrics = try values.decode(PrototypeMetrics.self, forKey: .settings).metrics
    }
    func converted() -> CustomKeyboardLayout { .init(name: name, rows: rows, metrics: metrics) }
}

private struct PrototypeSlot: Decodable {
    var id: String
    var key: String
    var width: Double
    private enum CodingKeys: String, CodingKey { case id, key, width }
    init(from decoder: Decoder) throws {
        if let token = try? decoder.singleValueContainer().decode(String.self) {
            id = UUID().uuidString; key = token; width = 1; return
        }
        try rejectUnknownFields(decoder, allowed: ["id", "key", "width"])
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(String.self, forKey: .id)
        guard let token = try values.decodeIfPresent(String.self, forKey: .key) else {
            throw invalid("原型仍有空白槽位，请分配键位后再导出。")
        }
        key = token; width = try values.decode(Double.self, forKey: .width)
    }
    func converted() throws -> CustomKeyboardKey {
        if key.hasPrefix("group:") { throw invalid("共键布局需要配套的输入解码方案，暂不能应用。") }
        if key == "fn:comma" || key == "fn:period" {
            return .init(id: id, text: key == "fn:comma" ? "，" : "。", width: width)
        }
        if key.hasPrefix("fn:") {
            let token = String(key.dropFirst(3))
            guard let action = token == "return" ? .enter : CustomKeyAction(rawValue: token), action != .character else {
                throw invalid("暂不支持功能键 \(key)。请先从布局中移除。")
            }
            return .init(id: id, action: action, width: width)
        }
        return .init(id: id, text: key, width: width)
    }
}

private struct PrototypeMetrics: Decodable {
    var metrics: CustomKeyboardMetrics
    private enum CodingKeys: String, CodingKey { case height, gap, padding, splitGap, split, stagger }
    init(from decoder: Decoder) throws {
        try rejectUnknownFields(decoder, allowed: ["height", "gap", "padding", "splitGap", "split", "stagger"])
        let values = try decoder.container(keyedBy: CodingKeys.self)
        metrics = try .init(keyHeight: values.decode(Double.self, forKey: .height),
                            gap: values.decode(Double.self, forKey: .gap),
                            padding: values.decode(Double.self, forKey: .padding),
                            splitGap: values.decode(Double.self, forKey: .splitGap),
                            split: values.decode(Bool.self, forKey: .split),
                            stagger: values.decode(Bool.self, forKey: .stagger))
    }
}

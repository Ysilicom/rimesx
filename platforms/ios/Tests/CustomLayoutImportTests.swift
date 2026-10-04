import XCTest
import RimesCore
import ZIPFoundation
@testable import RIMES

final class CustomLayoutImportTests: XCTestCase {
    func testHamsterImportKeepsCharactersFunctionsAndProportionsAsDraft() throws {
        let report = try CustomLayoutImportService.inspect(data: Data(hamsterFixture.utf8), filename: "hamster.yaml")
        XCTAssertEqual(report.layouts.count, 1)
        let layout = try XCTUnwrap(report.layouts.first)
        XCTAssertEqual(layout.rows.map(\.count), [10, 9, 9, 3])
        XCTAssertEqual(layout.rows[0].map(\.text).joined(), "qwertyuiop")
        XCTAssertEqual(layout.rows[2].first?.action, .shift)
        XCTAssertEqual(layout.rows[2].last?.action, .backspace)
        XCTAssertEqual(layout.rows[3].map(\.action), [.numbers, .space, .enter])
        XCTAssertEqual(layout.rows[3][1].width / layout.rows[3][0].width, 5, accuracy: 0.00001)
        XCTAssertEqual(layout.metrics.gap, 4)
        XCTAssertTrue(layout.metrics.stagger)
        XCTAssertTrue(report.warnings.contains { $0.contains("滑动") && $0.contains("长按") })
        XCTAssertTrue(report.warnings.contains { $0.contains("characterMargin") })
        XCTAssertTrue(report.warnings.contains { $0.contains("横屏") })
        XCTAssertTrue(report.warnings.contains { $0.contains("内置数字层") })
        XCTAssertTrue(report.details.contains { $0.contains("数字") && $0.contains("未转换") })
        XCTAssertThrowsError(try layout.validated(), "The source lacks a language function; import must not silently apply it.")
        var completed = layout
        completed.rows[3].append(.init(action: .language))
        XCTAssertNoThrow(try completed.validated())
    }

    func testZIPReadsOnlyLayoutsAndReportsUninstalledEngineFiles() throws {
        let zip = try archive([
            ("sample/hamster.yaml", Data(hamsterFixture.utf8), .file),
            ("sample/hamster.custom.yaml", Data("patch:\n  keyboard/+: {useKeyboardType: chinese}\n".utf8), .file),
            ("sample/example.schema.yaml", Data("schema: {schema_id: example}".utf8), .file),
            ("sample/example.dict.yaml", Data("name: example".utf8), .file),
            ("sample/lua/do_not_run.lua", Data("error('must never execute')".utf8), .file),
            ("sample/installation.yaml", Data("private_installation_metadata: do_not_read".utf8), .file)
        ])
        let result = try CustomLayoutImportService.inspect(data: zip, filename: "sample.zip")
        XCTAssertEqual(result.layouts.count, 1)
        XCTAssertTrue(result.details.contains { $0.contains("1 个方案、1 个词典、1 个 Lua") })
        XCTAssertTrue(result.warnings.contains { $0.contains("没有安装或执行") })
        XCTAssertFalse((result.details + result.warnings).joined().contains("private_installation_metadata"))
    }

    func testNativeJSONImportsAsNewCopyWithoutChangingKeyIdentity() throws {
        let original = try XCTUnwrap(CustomKeyboardLayout.templates.first)
        let result = try CustomLayoutImportService.inspect(data: original.exportData(), filename: "layout.json")
        let copy = try XCTUnwrap(result.layouts.first)
        XCTAssertNotEqual(original.id, copy.id)
        XCTAssertEqual(original.rows, copy.rows)
        XCTAssertEqual(original.metrics, copy.metrics)
    }

    func testRejectsUnsafeArchivePathsSymlinksAndDuplicates() throws {
        for path in ["../hamster.yaml", "/hamster.yaml", "nested/../../hamster.yaml", "nested\\hamster.yaml"] {
            let zip = try archive([(path, Data(hamsterFixture.utf8), .file)])
            XCTAssertThrowsError(try CustomLayoutImportService.inspect(data: zip, filename: "bad.zip"), path)
        }
        let link = try archive([("hamster.yaml", Data("/outside".utf8), .symlink)])
        XCTAssertThrowsError(try CustomLayoutImportService.inspect(data: link, filename: "bad.zip"))
        let duplicate = try archive([("hamster.yaml", Data(hamsterFixture.utf8), .file),
                                     ("HAMSTER.YAML", Data(hamsterFixture.utf8), .file)])
        XCTAssertThrowsError(try CustomLayoutImportService.inspect(data: duplicate, filename: "bad.zip"))
    }

    func testRejectsEncryptedAndCorruptZIPBeforeImport() throws {
        let zip = try archive([("hamster.yaml", Data(hamsterFixture.utf8), .file)])
        var encrypted = zip
        let central = try XCTUnwrap(encrypted.range(of: Data([0x50, 0x4b, 0x01, 0x02]))).lowerBound
        encrypted[central + 8] |= 1
        XCTAssertThrowsError(try CustomLayoutImportService.inspect(data: encrypted, filename: "bad.zip"))
        var damaged = zip
        let contents = try XCTUnwrap(damaged.range(of: Data("keyboards:".utf8))).lowerBound
        damaged[contents] ^= 1
        XCTAssertThrowsError(try CustomLayoutImportService.inspect(data: damaged, filename: "bad.zip"))
    }

    func testRejectsExcessiveCompressionAndEntryCount() throws {
        let compressed = try archive([("huge.txt", Data(repeating: 0x20, count: 100_000), .file)], compression: .deflate)
        XCTAssertThrowsError(try CustomLayoutImportService.inspect(data: compressed, filename: "bomb.zip"))
        let many = try archive((0..<513).map { ("entry\($0)", Data(), Entry.EntryType.file) })
        XCTAssertThrowsError(try CustomLayoutImportService.inspect(data: many, filename: "many.zip"))
    }

    func testRejectsYAMLTagsDuplicateKeysCyclesAndUnboundedNesting() throws {
        let fixtures = [
            "keyboards: !!python/object {}",
            "keyboards: []\nkeyboards: []",
            "keyboards: &recursive [*recursive]",
            "keyboards: " + String(repeating: "[", count: 80) + String(repeating: "]", count: 80)
        ]
        for fixture in fixtures {
            XCTAssertThrowsError(try CustomLayoutImportService.inspect(data: Data(fixture.utf8), filename: "bad.yaml"))
        }
        var expansion = "a: &a [x, x]\n"
        for value in 0..<20 {
            let previous = value == 0 ? "a" : "n\(value - 1)"
            expansion += "n\(value): &n\(value) [*\(previous), *\(previous)]\n"
        }
        XCTAssertThrowsError(try CustomLayoutImportService.inspect(data: Data(expansion.utf8), filename: "aliases.yaml"))
    }

    func testRejectsUnknownVersionAndDoesNotPretendSchemaIsLayout() throws {
        var value = CustomKeyboardLayout.templates[0]; value.formatVersion = 99
        XCTAssertThrowsError(try CustomLayoutImportService.inspect(data: JSONEncoder().encode(value), filename: "unknown.json"))
        let schema = try CustomLayoutImportService.inspect(data: Data("schema: {schema_id: demo}\n".utf8), filename: "demo.schema.yaml")
        XCTAssertTrue(schema.layouts.isEmpty)
        let zip = try archive([("demo.schema.yaml", Data("schema: {schema_id: demo}".utf8), .file)])
        XCTAssertThrowsError(try CustomLayoutImportService.inspect(data: zip, filename: "scheme.zip"))
    }

    func testYAMLImplicitEqualsTagInNumericLayerDoesNotRejectMainLayout() throws {
        let fixture = hamsterFixture.replacingOccurrences(of: "char: '1'", with: "char: =")
        let report = try CustomLayoutImportService.inspect(data: Data(fixture.utf8), filename: "hamster.yaml")
        XCTAssertEqual(report.layouts.count, 1, "YAML 1.1 resolves '=' as the benign value tag.")
    }

    func testYAMLPreflightCannotBeBypassedByPlainQuotesOrInlineBlockSequences() throws {
        let deep = String(repeating: "[", count: 80) + String(repeating: "]", count: 80)
        let fixtures = [
            "note: don't hide the next line\nkeyboards: " + deep,
            "keyboards: [don't, " + deep + "]",
            "keyboards: \"unfinished\n" + deep,
            "keyboards:\n  " + String(repeating: "- ", count: 80) + "value",
            "keyboards: [\n [\n []\n ]\n]"
        ]
        for fixture in fixtures {
            XCTAssertThrowsError(try CustomLayoutImportService.inspect(data: Data(fixture.utf8), filename: "bad.yaml"))
        }
    }

    func testOutOfBoundsWidthsAreReportedWithoutCreatingApplicableLayout() throws {
        let malformed = hamsterFixture.replacingOccurrences(of: "percentage: 0.14", with: "percentage: 0.95")
        let report = try CustomLayoutImportService.inspect(data: Data(malformed.utf8), filename: "hamster.yaml")
        XCTAssertTrue(report.layouts.isEmpty)
        XCTAssertTrue(report.warnings.contains { $0.contains("宽度超过") })
    }

    private func archive(_ values: [(String, Data, Entry.EntryType)], compression: CompressionMethod = .none) throws -> Data {
        let archive = try Archive(data: Data(), accessMode: .create)
        for (path, data, type) in values {
            try archive.addEntry(with: path, type: type, uncompressedSize: Int64(data.count), compressionMethod: compression) { position, size in
                data.subdata(in: Int(position)..<Int(position) + size)
            }
        }
        return try XCTUnwrap(archive.data)
    }

    /// Synthetic fixture of the observed Hamster format; no dictionary or private package data.
    private var hamsterFixture: String {
        func character(_ c: Character) -> String {
            "{action: {character: {char: \"\(c)\"}}, width: {percentage: 0.1}}"
        }
        let first = "qwertyuiop".map(character).joined(separator: ", ")
        let second = "asdfghjkl".map(character).joined(separator: ", ")
        let third = "zxcvbnm".map(character).joined(separator: ", ")
        return """
        style: &basic {buttonBackgroundColor: '0xffffff'}
        keyboards:
          - name: 普通字母
            isPrimary: true
            keyStyle: {basic: *basic}
            buttonInsets: {left: 2, right: 2, top: 2.5, bottom: 2.5}
            rows:
              - keys: [\(first)]
              - keys: [{action: {characterMargin: {char: a}}, width: available}, \(second), {action: {characterMargin: {char: l}}, width: available}]
              - keys: [{action: shift, width: {portrait: {percentage: 0.14}, landscape: {percentage: 0.13}}, swipe: [{action: {shortcutCommand: '#中英切换'}, direction: up}], callout: [{action: {shortcutCommand: '#RimeSwitcher'}}]}, \(third), {action: backspace, width: available}]
              - keys: [{action: {keyboardType: '123'}, width: {percentage: 0.14}}, {action: space, width: {percentage: 0.7}}, {action: enter, width: available}]
          - name: 数字
            rows:
              - keys: [{action: {character: {char: '1'}}, width: available}]
        """
    }
}

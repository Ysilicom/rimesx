import XCTest
@testable import RimesCore

final class CustomKeyboardLayoutTests: XCTestCase {
    func testTemplatesValidateAndRoundTripWithoutChangingKeyIdentity() throws {
        for template in CustomKeyboardLayout.templates {
            XCTAssertEqual(try template.validated(), template)
            XCTAssertEqual(try CustomKeyboardLayout.importData(template.exportData()), template)
            XCTAssertEqual(template.rows.flatMap { $0 }.filter { $0.action == .character }.count, 26)
        }
        XCTAssertEqual(CustomKeyboardLayout.templates, CustomKeyboardLayout.templates)
    }

    func testNativeImportRejectsUnknownFieldsAndFutureVersions() throws {
        let original = try nativeObject()
        var changed = original; changed["formatVersion"] = 2
        assertImportFails(changed, containing: "版本")
        changed = original; changed["execute"] = "anything"
        assertImportFails(changed, containing: "execute")
        changed = original
        var metrics = try XCTUnwrap(changed["metrics"] as? [String: Any])
        metrics["screenHeight"] = 600; changed["metrics"] = metrics
        assertImportFails(changed, containing: "screenHeight")
        changed = original
        var rows = try XCTUnwrap(changed["rows"] as? [[[String: Any]]])
        rows[0][0]["longPress"] = "abc"; changed["rows"] = rows
        assertImportFails(changed, containing: "longPress")
        changed = original; changed.removeValue(forKey: "metrics")
        assertImportFails(changed)
    }

    func testValidationRejectsUnusableCoverageAndDuplicateActions() throws {
        var layout = CustomKeyboardLayout.templates[0]
        layout.rows[0][0].text = ""
        assertInvalid(layout, containing: "空白")
        layout.rows[0][0].text = "ab"
        assertInvalid(layout, containing: "解码")
        layout.rows[0][0].text = "a"
        assertInvalid(layout, containing: "缺少字母")
        layout = CustomKeyboardLayout.templates[0]
        layout.rows[0].append(.init(text: "a"))
        assertInvalid(layout, containing: "不能重复")
        layout = CustomKeyboardLayout.templates[0]
        layout.rows[3].removeAll { $0.action == .language }
        assertInvalid(layout, containing: "中 / 英")
        layout = CustomKeyboardLayout.templates[0]
        layout.rows[3].append(.init(action: .backspace))
        assertInvalid(layout, containing: "不能重复")
        layout = CustomKeyboardLayout.templates[0]
        layout.rows[0][0].id = layout.rows[0][1].id
        assertInvalid(layout, containing: "标识")
        layout = CustomKeyboardLayout.templates[0]
        layout.rows[3][0].text = "abc"
        assertInvalid(layout, containing: "功能键不能附带")
    }

    func testValidationRejectsNonFiniteAndOutOfRangeDimensions() {
        for badValue in [Double.nan, .infinity, -.infinity, -1, 200] {
            var layout = CustomKeyboardLayout.templates[0]
            layout.metrics.gap = badValue
            assertInvalid(layout)
            layout = CustomKeyboardLayout.templates[0]
            layout.rows[0][0].width = badValue
            assertInvalid(layout)
        }
        var layout = CustomKeyboardLayout.templates[0]
        layout.metrics.padding = 28
        XCTAssertNoThrow(try layout.validated()) // Existing HTML prototype permits 28 pt.
        layout.metrics.keyHeight = 29
        assertInvalid(layout)
        layout = CustomKeyboardLayout.templates[0]
        layout.metrics.splitGap = 61
        assertInvalid(layout)
    }

    func testWhitespaceControlAndUppercaseCharactersAreRejected() {
        for value in [" ", "\n", "\0", "\t", "Q"] {
            var layout = CustomKeyboardLayout.templates[0]
            layout.rows[0][0].text = value
            assertInvalid(layout)
        }
        var layout = CustomKeyboardLayout.templates[0]
        layout.rows[2].append(.init(text: "，"))
        layout.rows[2].append(.init(text: "."))
        XCTAssertNoThrow(try layout.validated())
    }

    func testImportExistingPrototypeExportPreservesPositionsAndMetrics() throws {
        var prototype = prototypeObject()
        prototype["prototypeOnly"] = true
        prototype["exportedAt"] = "2026-10-02T00:00:00Z"
        prototype["coordinateUnit"] = "pt"
        prototype["previewWidth"] = 393
        let layout = try CustomKeyboardLayout.importData(JSONSerialization.data(withJSONObject: prototype))
        XCTAssertEqual(layout.name, "已保存的原型")
        XCTAssertEqual(layout.rows[0].map(\.text).joined(), "qwertyuiop")
        XCTAssertEqual(layout.rows[3].map(\.action), [.numbers, .buffer, .language, .space, .backspace, .enter])
        XCTAssertEqual(layout.rows[3][3].width, 2.8)
        XCTAssertEqual(layout.rows[0][0].id, "letter-q")
        XCTAssertEqual(layout.metrics, .init(keyHeight: 48, gap: 4, padding: 28, splitGap: 20, split: true, stagger: false))
        XCTAssertEqual(try CustomKeyboardLayout.importData(layout.exportData()), layout)
    }

    func testPrototypeImportSupportsStringTokensAndRejectsGroupedOrEmptySlots() throws {
        var prototype = prototypeObject()
        let rowsOfTokens: [[String]] = ["qwertyuiop", "asdfghjkl", "zxcvbnm"].map { row in row.map { String($0) } }
        prototype["rows"] = rowsOfTokens
        prototype["functions"] = ["fn:numbers", "fn:language", "fn:space", "fn:backspace", "fn:return", "fn:comma", "fn:period"]
        let imported = try CustomKeyboardLayout.importData(JSONSerialization.data(withJSONObject: prototype))
        XCTAssertEqual(imported.rows.last?.suffix(2).map(\.text), ["，", "。"])
        prototype["grouped"] = true
        assertImportFails(prototype, containing: "共键")
        prototype = prototypeObject()
        var rows = try XCTUnwrap(prototype["rows"] as? [[[String: Any]]])
        rows[0][0]["key"] = NSNull(); prototype["rows"] = rows
        assertImportFails(prototype, containing: "空白")
        rows[0][0]["key"] = "group:qw"; prototype["rows"] = rows
        assertImportFails(prototype, containing: "共键")
        rows[0][0]["key"] = "fn:settings"; prototype["rows"] = rows
        assertImportFails(prototype, containing: "fn:settings")
    }

    func testImportRejectsNonLayoutFilesAndOversizedPayloads() {
        for data in [Data("not json".utf8), Data("{\"schema\":{\"schema_id\":\"xingmao\"}}".utf8),
                     Data(repeating: 32, count: 262_145)] {
            XCTAssertThrowsError(try CustomKeyboardLayout.importData(data))
        }
    }

    func testGeometryFitsAtPhoneAndLandscapeWidthsForEveryTemplate() throws {
        for template in CustomKeyboardLayout.templates {
            for width in [320.0, 393, 852] {
                for landscape in [false, true] {
                    try assertFits(template, width: width, landscape: landscape)
                }
            }
        }
    }

    func testMixedRowsWeightedKeysAndMaximumMetricsDoNotOverlap() throws {
        var layout = CustomKeyboardLayout.templates[2]
        layout.metrics = .init(keyHeight: 72, gap: 12, padding: 28, splitGap: 60, split: true, stagger: true)
        layout.rows[0][0].width = 4
        layout.rows[1][0].width = 0.5
        let shift = layout.rows[3].remove(at: 1)
        layout.rows[2].insert(shift, at: 0)
        assertInvalid(layout, containing: "过窄")
        for width in [320.0, 393, 852] { try assertFits(layout, width: width, validate: false) }
    }

    func testNarrowFunctionKeysCannotBeAppliedOrExported() {
        var layout = CustomKeyboardLayout.templates[0]
        for index in layout.rows[3].indices { layout.rows[3][index].width = 4 }
        let delete = layout.rows[3].firstIndex { $0.action == .backspace }!
        layout.rows[3][delete].width = 0.5
        assertInvalid(layout, containing: "过窄")
        XCTAssertThrowsError(try layout.exportData())
    }

    func testSameGeometryServesDraftPreviewAndContentHeight() {
        var layout = CustomKeyboardLayout.templates[0]
        let before = layout.geometry(width: 393)
        layout.rows[0][0].text = "" // Drafts stay previewable until Apply validates them.
        let draft = layout.geometry(width: 393)
        XCTAssertEqual(draft.frames.count, before.frames.count)
        XCTAssertEqual(draft.height, before.height)
        XCTAssertEqual(draft.frames.first?.key.label, "＋")
        layout.metrics.keyHeight += 10
        XCTAssertEqual(layout.geometry(width: 393).height, before.height + Double(layout.rows.count) * 10)
        XCTAssertLessThan(layout.geometry(width: 393, landscape: true).height, layout.geometry(width: 393).height)
        XCTAssertEqual(layout.geometry(width: .nan).frames, [])
    }

    func testOrthogonalColumnsAlignAndSplitLeavesRequestedGap() throws {
        let orthogonal = CustomKeyboardLayout.templates[1]
        let frames = orthogonal.geometry(width: 393).frames
        let q = try XCTUnwrap(frames.first { $0.key.text == "q" })
        let a = try XCTUnwrap(frames.first { $0.key.text == "a" })
        let z = try XCTUnwrap(frames.first { $0.key.text == "z" })
        XCTAssertEqual(q.x, a.x); XCTAssertEqual(a.x, z.x)
        let split = CustomKeyboardLayout.templates[2]
        let splitFrames = split.geometry(width: 393).frames
        let t = try XCTUnwrap(splitFrames.first { $0.key.text == "t" })
        let y = try XCTUnwrap(splitFrames.first { $0.key.text == "y" })
        XCTAssertEqual(y.x - (t.x + t.width), split.metrics.splitGap, accuracy: 0.001)
    }

    private func assertFits(_ layout: CustomKeyboardLayout, width: Double, landscape: Bool = false, validate: Bool = true,
                            file: StaticString = #filePath, line: UInt = #line) throws {
        if validate { _ = try layout.validated() }
        let geometry = layout.geometry(width: width, landscape: landscape)
        XCTAssertEqual(geometry.frames.count, layout.rows.flatMap { $0 }.count, file: file, line: line)
        for frame in geometry.frames {
            XCTAssertGreaterThan(frame.width, 0, file: file, line: line)
            XCTAssertGreaterThan(frame.height, 0, file: file, line: line)
            XCTAssertGreaterThanOrEqual(frame.x, 0, file: file, line: line)
            XCTAssertLessThanOrEqual(frame.x + frame.width, width + 0.001, file: file, line: line)
            XCTAssertLessThanOrEqual(frame.y + frame.height, geometry.height, file: file, line: line)
        }
        for (index, first) in geometry.frames.enumerated() {
            for second in geometry.frames.dropFirst(index + 1) {
                let overlapWidth = min(first.x + first.width, second.x + second.width) - max(first.x, second.x)
                let overlapHeight = min(first.y + first.height, second.y + second.height) - max(first.y, second.y)
                XCTAssertFalse(overlapWidth > 0.001 && overlapHeight > 0.001, "\(first.key.label) overlaps \(second.key.label)", file: file, line: line)
            }
        }
    }

    private func assertInvalid(_ layout: CustomKeyboardLayout, containing expected: String? = nil,
                               file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertThrowsError(try layout.validated(), file: file, line: line) { error in
            if let expected { XCTAssertTrue(error.localizedDescription.contains(expected), error.localizedDescription, file: file, line: line) }
        }
    }

    private func assertImportFails(_ object: [String: Any], containing expected: String? = nil,
                                   file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertThrowsError(try CustomKeyboardLayout.importData(JSONSerialization.data(withJSONObject: object)), file: file, line: line) { error in
            if let expected { XCTAssertTrue(error.localizedDescription.contains(expected), error.localizedDescription, file: file, line: line) }
        }
    }

    private func nativeObject() throws -> [String: Any] {
        try XCTUnwrap(JSONSerialization.jsonObject(with: CustomKeyboardLayout.templates[0].exportData()) as? [String: Any])
    }

    private func prototypeObject() -> [String: Any] {
        ["version": 1, "name": "已保存的原型", "template": "split", "grouped": false,
         "rows": ["qwertyuiop", "asdfghjkl", "zxcvbnm"].map { row in row.map { ["id": "letter-\($0)", "key": String($0), "width": 1] as [String: Any] } },
         "functions": ["numbers", "buffer", "language", "space", "backspace", "return"].map { ["id": "function-\($0)", "key": "fn:\($0)", "width": $0 == "space" ? 2.8 : 1] as [String: Any] },
         "settings": ["height": 48, "gap": 4, "padding": 28, "splitGap": 20, "split": true, "stagger": false]]
    }
}

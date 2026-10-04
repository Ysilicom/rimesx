import XCTest
@testable import RimesCore

final class NineKeyPinyinTests: XCTestCase {
    func testSelectingSyllablesKeepsUnresolvedDigitsAndExplicitBoundaries() {
        let spelling = NineKeyPinyin(syllables: ["ni", "mi", "hao", "gao", "xi", "an", "lv"])
        XCTAssertEqual(NineKeyPinyin.digits(for: "nihao"), "64426")
        XCTAssertEqual(NineKeyPinyin.digits(for: "lv"), "58")
        XCTAssertEqual(Set(spelling.choices(for: "64426")), ["ni", "mi"])
        XCTAssertEqual(spelling.selecting("ni", in: "64426"), "ni'426")
        XCTAssertEqual(spelling.selecting("hao", in: "ni'426"), "ni'hao'")
        XCTAssertEqual(spelling.selecting("xi", in: "94'26"), "xi'26")
        XCTAssertNil(spelling.selecting("hao", in: "64426"))
        XCTAssertNil(spelling.selecting("unknown", in: "64426"))
        XCTAssertTrue(spelling.choices(for: "ni'hao'").isEmpty)
    }
    func testDeleteEditsDigitsAndUndoesAConfirmedSyllableBeforeDeletingIt() {
        XCTAssertEqual(NineKeyPinyin.backspacing("ni'426"), "ni'42")
        XCTAssertEqual(NineKeyPinyin.backspacing("ni'hao'"), "ni'426")
        XCTAssertEqual(NineKeyPinyin.backspacing("ni'"), "64")
        XCTAssertEqual(NineKeyPinyin.backspacing("64'"), "64")
        XCTAssertEqual(NineKeyPinyin.backspacing("6"), "")
        XCTAssertEqual(NineKeyPinyin.backspacing(""), "")
    }
    func testOlderPreferencesKeepChordSelectionAndDefaultOrdinaryAppearance() throws {
        let data = Data(#"{"scheme":"chord","chordLayout":"splitOrthogonal","hapticStrength":"strong","initialized":true}"#.utf8)
        let value = try JSONDecoder().decode(KeyboardPreferences.self, from: data)
        XCTAssertEqual(value.scheme, .chord); XCTAssertEqual(value.chordLayout, .splitOrthogonal)
        XCTAssertEqual(value.hapticStrength, .strong)
        XCTAssertEqual(value.ordinaryLayout, .qwerty); XCTAssertEqual(value.keyboardSkin, .system)
    }
}

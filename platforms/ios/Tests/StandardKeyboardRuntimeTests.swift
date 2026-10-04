import XCTest
import UIKit
import RimesCore
@testable import RIMES

@MainActor final class StandardKeyboardRuntimeTests: XCTestCase {
    func testKeySoundsRemainIndependentOfHapticsAndDoNotClickForChordPreviewOrCommit() {
        let feedback = KeyboardFeedback()
        var clicks = 0, pulses = 0, time: TimeInterval = 0
        feedback.clock = { time }; feedback.playInputClick = { clicks += 1 }
        feedback.onFeedback = { _ in pulses += 1 }
        feedback.enabled = false
        feedback.send(.press)
        XCTAssertEqual(clicks, 1); XCTAssertEqual(pulses, 0)
        time = 0.1; feedback.send(.selection, combination: "aj")
        time = 0.2; feedback.send(.commit)
        XCTAssertEqual(clicks, 1)
        time = 0.3; feedback.send(.press); feedback.send(.press)
        XCTAssertEqual(clicks, 2)
        feedback.soundEnabled = false; feedback.enabled = true
        time = 0.4; feedback.send(.press)
        XCTAssertEqual(clicks, 2); XCTAssertEqual(pulses, 1)
        let (window, keyboard) = host(); defer { window.isHidden = true }
        XCTAssertEqual(keyboard.inputView?.enableInputClicksWhenVisible, true)
        XCTAssertEqual(keyboard.inputView?.allowsSelfSizing, true)
    }
    func testStandardGeometryFitsAndPlacesQwertyUtilitiesAtTheThirdRow() throws {
        for width: CGFloat in [310, 383, 842] {
            for mode in [StandardKeyboardMode.qwerty, .nineKey, .numeric, .symbols] {
                let geometry = StandardKeyboardGeometry.make(width: width, mode: mode, landscape: width > 600)
                let frames = geometry.keys.map(\.frame) + Array(geometry.controls.values)
                for (i, frame) in frames.enumerated() {
                    XCTAssertGreaterThan(frame.width, 20)
                    XCTAssertGreaterThanOrEqual(frame.minX, -0.01)
                    XCTAssertLessThanOrEqual(frame.maxX, width + 0.01)
                    XCTAssertLessThanOrEqual(frame.maxY, geometry.height + 0.01)
                    for other in frames.dropFirst(i + 1) { XCTAssertFalse(frame.intersects(other)) }
                }
                if mode == .qwerty {
                    XCTAssertEqual(geometry.keys.count, 26)
                    let z = try XCTUnwrap(geometry.keys.first { $0.text == "z" })
                    XCTAssertEqual(geometry.controls[.shift]?.minY, z.frame.minY)
                    XCTAssertEqual(geometry.controls[.backspace]?.minY, z.frame.minY)
                    XCTAssertGreaterThan(try XCTUnwrap(geometry.controls[.space]).width, width * 0.4)
                }
            }
        }
    }
    func testNineKeyTypesSelectsSpellingAndDeletesInHostAndBuffer() throws {
        for buffered in [false, true] {
            let (window, keyboard) = host(); defer { window.isHidden = true }
            keyboard.developmentOrdinaryAppearance(layout: .nineKey)
            if buffered { keyboard.developmentBuffer("") }
            keyboard.developmentType("64426")
            XCTAssertEqual(keyboard.developmentRaw, "64426")
            XCTAssertEqual(keyboard.layoutViews.keys.standardMode, .nineKey)
            keyboard.developmentSelectNineKeySpelling("ni")
            XCTAssertEqual(keyboard.developmentRaw, "ni'426")
            keyboard.developmentBackspace(); XCTAssertEqual(keyboard.developmentRaw, "ni'42")
            keyboard.developmentType("6")
            let candidate = try XCTUnwrap(descendants(keyboard.layoutViews.candidates).first { $0.accessibilityLabel == "你好" } as? UIButton)
            candidate.sendActions(for: .touchUpInside)
            XCTAssertEqual(buffered ? keyboard.developmentBufferSource.text : keyboard.layoutProxy.native.text, "你好")
            XCTAssertTrue(keyboard.developmentRaw.isEmpty)
            if buffered { XCTAssertTrue(keyboard.layoutProxy.native.text.isEmpty) }
        }
    }
    func testNineKeyNumericAndEnglishSwitchesDoNotDecodeLiteralDigitsOrLetters() throws {
        let (window, keyboard) = host(); defer { window.isHidden = true }
        keyboard.developmentOrdinaryAppearance(layout: .nineKey)
        keyboard.developmentNumeric(); keyboard.developmentType("123")
        XCTAssertEqual(keyboard.layoutProxy.native.text, "123")
        XCTAssertTrue(keyboard.developmentRaw.isEmpty)
        keyboard.developmentNumeric(); keyboard.developmentLanguage(); keyboard.developmentType("abc")
        XCTAssertEqual(keyboard.layoutProxy.native.text, "123abc")
        XCTAssertEqual(keyboard.layoutViews.keys.standardMode, .qwerty)
        keyboard.developmentLanguage()
        XCTAssertEqual(keyboard.layoutViews.keys.standardMode, .nineKey)
    }
    func testAppliedCustomLayoutTakesPrecedenceOverNineKeyPreference() throws {
        let (window, keyboard) = host(); defer { window.isHidden = true }
        keyboard.developmentOrdinaryAppearance(layout: .nineKey)
        let custom = CustomKeyboardLayout.templates[0]
        keyboard.developmentSetCustomLayout(custom)
        keyboard.developmentChoose(.pinyin)
        XCTAssertEqual(keyboard.layoutViews.keys.customLayout, custom)
        XCTAssertNil(keyboard.layoutViews.keys.standardMode)
        keyboard.developmentType("nihao")
        let candidate = try XCTUnwrap(descendants(keyboard.layoutViews.candidates).first { $0.accessibilityLabel == "你好" } as? UIButton)
        candidate.sendActions(for: .touchUpInside)
        XCTAssertEqual(keyboard.layoutProxy.native.text, "你好")
        keyboard.developmentSetCustomLayout(nil)
        keyboard.developmentChoose(.pinyin)
        XCTAssertEqual(keyboard.layoutViews.keys.standardMode, .nineKey)
        keyboard.developmentType("64426")
        XCTAssertNotNil(descendants(keyboard.layoutViews.candidates).first { $0.accessibilityLabel == "你好" })
    }
    func testSkinChangesPreserveFramesAndChordMappingAcrossOrdinarySettings() {
        let (window, keyboard) = host(); defer { window.isHidden = true }
        keyboard.developmentOrdinaryAppearance(layout: .qwerty, skin: .system)
        window.layoutIfNeeded()
        let original = keyboard.layoutViews.keys.developmentKeyFrames
        keyboard.developmentOrdinaryAppearance(layout: .qwerty, skin: .rimes)
        window.layoutIfNeeded()
        XCTAssertEqual(keyboard.layoutViews.keys.developmentKeyFrames, original)
        for layout in ChordLayout.allCases {
            keyboard.developmentChoose(.chord); keyboard.developmentSetLayout(layout); window.layoutIfNeeded()
            let chord = keyboard.layoutViews.keys.developmentKeyFrames
            keyboard.developmentOrdinaryAppearance(layout: .nineKey, skin: .system); window.layoutIfNeeded()
            XCTAssertEqual(keyboard.layoutViews.keys.developmentKeyFrames, chord)
            XCTAssertTrue(keyboard.layoutViews.keys.resolvesChords)
            XCTAssertEqual(keyboard.layoutViews.keys.skin, .system)
        }
    }
    func testSystemGlobeReservesItsRowWithoutShrinkingStandardKeys() {
        let (window, keyboard) = host(); defer { window.isHidden = true }
        let keys = keyboard.layoutViews.keys.developmentKeyFrames
        let space = keyboard.developmentSpaceKey.frame
        keyboard.layoutNeedsInputModeSwitchKey = true; keyboard.developmentContent(); window.layoutIfNeeded()
        XCTAssertFalse(keyboard.layoutViews.globe.isHidden)
        XCTAssertFalse(keyboard.layoutViews.bottom.isHidden)
        XCTAssertEqual(keyboard.layoutViews.keys.developmentKeyFrames, keys)
        XCTAssertEqual(keyboard.developmentSpaceKey.frame, space)
    }
    func testAppearanceStoreLeavesSchemeAndCustomLayoutFilesUntouched() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let custom = CustomLayoutStore(root: root)
        try custom.apply(CustomKeyboardLayout.templates[0])
        let before = try Data(contentsOf: root.appendingPathComponent("keyboard-layouts-v1.json"))
        let store = KeyboardAppearanceStore(root: root)
        XCTAssertEqual(store.load().layout, .qwerty); XCTAssertEqual(store.load().skin, .system)
        try store.save(.init(layout: .nineKey, skin: .rimes))
        let saved = store.load()
        XCTAssertEqual(saved.layout, .nineKey); XCTAssertEqual(saved.skin, .rimes); XCTAssertNotNil(saved.revision)
        XCTAssertEqual(try Data(contentsOf: root.appendingPathComponent("keyboard-layouts-v1.json")), before)
    }
    private func host() -> (UIWindow, KeyboardViewController) {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 900))
        let parent = UIViewController(); window.rootViewController = parent; window.makeKeyAndVisible()
        let keyboard = KeyboardViewController(); keyboard.layoutNeedsInputModeSwitchKey = false
        keyboard.loadViewIfNeeded(); keyboard.developmentResetPreferences()
        parent.addChild(keyboard); parent.view.addSubview(keyboard.view); keyboard.didMove(toParent: parent)
        parent.view.layoutIfNeeded(); keyboard.view.layoutIfNeeded()
        return (window, keyboard)
    }
    private func descendants(_ view: UIView) -> [UIView] { [view] + view.subviews.flatMap(descendants) }
}

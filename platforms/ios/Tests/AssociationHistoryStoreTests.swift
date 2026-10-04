import XCTest
import UIKit
import RimesCore
@testable import RIMES

@MainActor final class AssociationHistoryStoreTests: XCTestCase {
    func testNoRequestPreservesExistingRecordsAndLegacyConfiguration() throws {
        let fixture = try Fixture(); defer { fixture.remove() }
        let history = learned("世界")
        XCTAssertTrue(try fixture.store.save(history, revision: nil))
        let before = try Data(contentsOf: fixture.file)
        XCTAssertFalse(try fixture.store.applyReset(nil))
        XCTAssertEqual(try Data(contentsOf: fixture.file), before)
        XCTAssertEqual(fixture.store.load(), history)
        let data = try JSONEncoder().encode(AppConfiguration())
        XCTAssertNil(try JSONDecoder().decode(AppConfiguration.self, from: data).associationResetRevision)
        XCTAssertFalse(String(decoding: data, as: UTF8.self).contains("associationResetRevision"))
    }

    func testResetRunsOnceAndOldControllersCannotRestoreClearedHistory() throws {
        let fixture = try Fixture(); defer { fixture.remove() }
        let old = learned("世界"), new = learned("你")
        try fixture.store.save(old, revision: nil)
        let revision = UUID()
        XCTAssertTrue(try fixture.store.applyReset(revision))
        XCTAssertTrue(fixture.store.load().isEmpty)
        XCTAssertEqual(fixture.store.resetRevision, revision)
        XCTAssertTrue(try fixture.store.save(new, revision: revision))
        let reopened = AssociationHistoryStore(root: fixture.root, defaults: fixture.defaults)
        XCTAssertFalse(try reopened.applyReset(revision))
        XCTAssertFalse(try reopened.applyReset(nil))
        XCTAssertEqual(reopened.load(), new)
        XCTAssertFalse(try fixture.store.save(old, revision: nil))
        XCTAssertEqual(reopened.load(), new)
        XCTAssertTrue(try reopened.applyReset(UUID()))
        XCTAssertTrue(reopened.load().isEmpty)
    }

    func testResetAcceptsMissingOrUnreadableHistoryWithoutChangingOtherFiles() throws {
        let fixture = try Fixture(); defer { fixture.remove() }
        let unrelated = fixture.root.appendingPathComponent("keep.txt")
        try Data("scheme data".utf8).write(to: unrelated)
        XCTAssertTrue(try fixture.store.applyReset(UUID()))
        try Data("invalid history json".utf8).write(to: fixture.file)
        XCTAssertTrue(try fixture.store.applyReset(UUID()))
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.file.path))
        XCTAssertEqual(try String(contentsOf: unrelated, encoding: .utf8), "scheme data")
    }

    func testKeyboardResetDoesNotEditDraftOrCompositionAndGearHasNoClearAction() throws {
        let fixture = try Fixture(); defer { fixture.remove() }
        try fixture.store.save(learned("世界"), revision: nil)
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 900))
        let parent = UIViewController(); window.rootViewController = parent; window.makeKeyAndVisible()
        defer { window.isHidden = true }
        let keyboard = KeyboardViewController(); keyboard.layoutNeedsInputModeSwitchKey = false
        keyboard.loadViewIfNeeded(); keyboard.developmentResetPreferences()
        parent.addChild(keyboard); parent.view.addSubview(keyboard.view); keyboard.didMove(toParent: parent)
        keyboard.developmentAssociationStore(fixture.store)
        keyboard.developmentBuffer("保留草稿")
        keyboard.developmentType("ni")
        XCTAssertFalse(fixture.store.load().isEmpty)
        let menuBeforeReset = try XCTUnwrap(keyboard.layoutViews.settings.menu)
        XCTAssertFalse(menuBeforeReset.children.contains { ["Clear learned associations", "清除联想记录"].contains($0.title) })
        let source = keyboard.developmentBufferSource
        keyboard.developmentApplyAssociationReset(UUID())
        XCTAssertTrue(fixture.store.load().isEmpty)
        XCTAssertEqual(keyboard.developmentBufferSource.text, source.text)
        XCTAssertEqual(keyboard.developmentBufferSource.revision, source.revision)
        XCTAssertEqual(keyboard.developmentRaw, "ni")
        XCTAssertTrue(keyboard.layoutProxy.insertions.isEmpty)
        func entries(_ menu: UIMenu) -> [UIMenuElement] {
            menu.children.flatMap { [$0] + (($0 as? UIMenu).map(entries) ?? []) }
        }
        let menu = try XCTUnwrap(keyboard.layoutViews.settings.menu)
        XCTAssertFalse(entries(menu).contains { ["Clear learned associations", "清除联想记录"].contains($0.title) })
    }

    private func learned(_ next: String) -> AssociationHistory {
        var history = AssociationHistory(); history.record(previous: "谢谢", next: next); return history
    }
    private struct Fixture {
        let root: URL, suite: String, defaults: UserDefaults, store: AssociationHistoryStore
        var file: URL { root.appendingPathComponent("RIMESAssociations.json") }
        init() throws {
            root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            suite = "org.scholay.rimes.associations.tests." + UUID().uuidString
            defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
            store = AssociationHistoryStore(root: root, defaults: defaults)
        }
        func remove() { defaults.removePersistentDomain(forName: suite); try? FileManager.default.removeItem(at: root) }
    }
}

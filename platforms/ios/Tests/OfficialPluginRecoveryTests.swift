import Foundation
import XCTest
import RimesCore
@testable import RIMES

final class OfficialPluginRecoveryTests: XCTestCase {
    func testExplicitRestoreRepairsCorruptAndOldReceiptsWithoutEnabling() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("PluginRepair-\(UUID())")
        defer { try? FileManager.default.removeItem(at: root) }
        let packages = root.appendingPathComponent("packages")
        let store = OfficialPluginStore(root: packages, platform: "ios", hostVersion: "1.1.0", catalog: try .bundled())
        try store.bootstrap()
        let userFile = root.appendingPathComponent("private-document.txt")
        try Data("preserved".utf8).write(to: userFile)
        let id = "builtin.apple-translation"
        let receipt = packages.appendingPathComponent(id + ".state.json")
        for obsolete in [false, true] {
            try store.setEnabled(true, id: id)
            let old = try XCTUnwrap(store.state(id))
            if obsolete {
                var json = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: receipt)) as? [String: Any])
                json["sha256"] = String(repeating: "0", count: 64)
                try JSONSerialization.data(withJSONObject: json).write(to: receipt)
            } else { try Data("corrupt receipt".utf8).write(to: receipt) }
            XCTAssertNil(store.state(id))
            XCTAssertFalse(store.isEnabled(legacyID: "apple.translation"))
            try await MobileOfficialPlugins.install(id, in: store)
            let repaired = try XCTUnwrap(store.state(id))
            XCTAssertTrue(repaired.installed); XCTAssertFalse(repaired.enabled)
            XCTAssertNotEqual(repaired.installationID, old.installationID)
            let entry = try XCTUnwrap(store.entries.first { $0.id == id })
            XCTAssertThrowsError(try store.install(OfficialPluginCatalog.bundledData(entry), id: id, expectedState: old))
            XCTAssertEqual(try String(contentsOf: userFile), "preserved")
        }
    }
}

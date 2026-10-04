import XCTest
@testable import RIMES
import RimesCore

final class RimeSchemeStoreTests: XCTestCase {
    private var temporary: URL!
    private var store: RimeSchemeStore!

    override func setUpWithError() throws {
        temporary = FileManager.default.temporaryDirectory.appendingPathComponent("RimeSchemeStoreTests-" + UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: temporary, withIntermediateDirectories: true)
        store = RimeSchemeStore(root: temporary.appendingPathComponent("schemes", isDirectory: true))
    }
    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: temporary)
        store = nil
        temporary = nil
    }

    func testPublishingDeployedPackagePreservesActiveSelectionAndRevision() throws {
        let first = package(schema: "first")
        try publish(first)
        let selection = RimeSchemeSelection(packageID: first.id, schemaID: "first")
        try store.activate(selection)
        let before = store.load()
        let second = package(schema: "second")
        let staging = try stage(second)

        try store.publish(stagedURL: staging, package: second)

        let after = store.load()
        XCTAssertEqual(after.packages.map(\.id), [first.id, second.id])
        XCTAssertEqual(after.active, selection)
        XCTAssertEqual(after.revision, before.revision)
        XCTAssertFalse(FileManager.default.fileExists(atPath: staging.path))
        XCTAssertNotNil(store.resolve(.init(packageID: second.id, schemaID: "second")))
    }

    func testActivationAndDeactivationOnlyChangeSchemeLibrarySelection() throws {
        let package = package(schema: "xmjd6")
        try publish(package)
        let installed = store.load()
        XCTAssertNil(installed.active)
        let selection = RimeSchemeSelection(packageID: package.id, schemaID: "xmjd6")

        try store.activate(selection)
        let enabled = store.load()
        XCTAssertEqual(enabled.active, selection)
        XCTAssertNotEqual(enabled.revision, installed.revision)
        try store.activate(nil)
        let disabled = store.load()
        XCTAssertNil(disabled.active)
        XCTAssertEqual(disabled.packages.map(\.id), [package.id])
        XCTAssertNotEqual(disabled.revision, enabled.revision)
    }

    func testIncompleteDeploymentDoesNotPublishOrRetireExistingSelection() throws {
        let first = package(schema: "first")
        try publish(first)
        let selection = RimeSchemeSelection(packageID: first.id, schemaID: "first")
        try store.activate(selection)
        let before = store.load()
        let incomplete = package(schema: "missing")
        let staging = store.stagingRoot.appendingPathComponent(incomplete.id, isDirectory: true)
        try FileManager.default.createDirectory(at: staging, withIntermediateDirectories: true)

        XCTAssertThrowsError(try store.publish(stagedURL: staging, package: incomplete))

        XCTAssertEqual(store.load().packages.map(\.id), [first.id])
        XCTAssertEqual(store.load().active, selection)
        XCTAssertEqual(store.load().revision, before.revision)
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.packageURL(id: incomplete.id).path))
    }

    func testRejectsInvalidIdentifiersAndStagingOutsideOwnedRoot() throws {
        for id in ["", ".", "..", "../xmjd6", "x/y", "x\\y", "x\ny", String(repeating: "x", count: 121)] {
            XCTAssertFalse(RimeSchemeStore.safeSchemaID(id), id)
        }
        XCTAssertTrue(RimeSchemeStore.safeSchemaID("xmjd6"))
        XCTAssertTrue(RimeSchemeStore.safeSchemaID("rimes.test-v1"))
        var invalid = package(schema: "valid")
        invalid.id = "../outside"
        XCTAssertThrowsError(try store.publish(stagedURL: temporary, package: invalid))
        let valid = package(schema: "valid")
        let outside = temporary.appendingPathComponent(valid.id, isDirectory: true)
        try writeDeployment(valid, at: outside)
        XCTAssertThrowsError(try store.publish(stagedURL: outside, package: valid))
        XCTAssertTrue(store.load().packages.isEmpty)
        XCTAssertThrowsError(try store.activate(.init(packageID: "../outside", schemaID: "../valid")))
        XCTAssertNil(store.load().active)
    }

    func testMissingPublishedSchemaCannotBeActivated() throws {
        let package = package(schema: "xmjd6")
        try publish(package)
        let before = store.load()
        let selection = RimeSchemeSelection(packageID: package.id, schemaID: "xmjd6")
        try FileManager.default.removeItem(at: store.packageURL(id: package.id).appendingPathComponent("build/xmjd6.schema.yaml"))

        XCTAssertNil(store.resolve(selection))
        XCTAssertThrowsError(try store.activate(selection))
        XCTAssertNil(store.load().active)
        XCTAssertEqual(store.load().revision, before.revision)
    }

    func testDirectoryNamedAsDeployedSchemaCannotBePublished() throws {
        let package = package(schema: "xmjd6")
        let staging = store.stagingRoot.appendingPathComponent(package.id, isDirectory: true)
        try FileManager.default.createDirectory(at: staging.appendingPathComponent("build/xmjd6.schema.yaml", isDirectory: true), withIntermediateDirectories: true)

        XCTAssertThrowsError(try store.publish(stagedURL: staging, package: package))
        XCTAssertTrue(store.load().packages.isEmpty)
        XCTAssertNil(store.load().active)
    }

    func testPublicationRollsBackWhenLibraryCannotBeWritten() throws {
        let package = package(schema: "fixture")
        let staging = try stage(package)
        try FileManager.default.createDirectory(at: store.root.appendingPathComponent("library-v1.json", isDirectory: true), withIntermediateDirectories: true)

        XCTAssertThrowsError(try store.publish(stagedURL: staging, package: package))
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.packageURL(id: package.id).path))
        XCTAssertTrue(store.load().packages.isEmpty)
        XCTAssertNil(store.load().active)
    }

    func testSchemeOperationsLeaveConfigurationAndChordPreferencesUnchanged() throws {
        var configuration = AppConfiguration()
        configuration.scheme = .chord
        configuration.schemeSelectionRevision = UUID()
        let configURL = temporary.appendingPathComponent("configuration-v1.json")
        let configData = try JSONEncoder().encode(configuration)
        try configData.write(to: configURL)
        let suite = "org.scholay.rimes.scheme-store-tests." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        var preferences = KeyboardPreferences()
        preferences.select(.chord)
        preferences.chordLayout = .splitOrthogonal
        preferences.englishInput = true
        preferences.hapticStrength = .strongest
        let preferenceStore = KeyboardPreferenceStore(defaults: defaults)
        preferenceStore.save(preferences)
        let beforePreferences = defaults.dictionaryRepresentation() as NSDictionary
        let package = package(schema: "xmjd6")

        try publish(package)
        try store.activate(.init(packageID: package.id, schemaID: "xmjd6"))
        try store.activate(nil)

        XCTAssertEqual(try Data(contentsOf: configURL), configData)
        XCTAssertEqual(defaults.dictionaryRepresentation() as NSDictionary, beforePreferences)
        let restored = preferenceStore.load()
        XCTAssertEqual(restored.scheme, .chord)
        XCTAssertEqual(restored.chordLayout, .splitOrthogonal)
        XCTAssertTrue(restored.englishInput)
        XCTAssertEqual(restored.hapticStrength, .strongest)
    }

    private func package(schema: String) -> RimeSchemePackage {
        .init(id: UUID().uuidString, name: "Test package", schemas: [.init(id: schema, name: "Test scheme")],
              importedAt: Date(timeIntervalSince1970: 1_700_000_000), sourceDigest: String(repeating: "a", count: 64), warnings: [])
    }
    private func stage(_ package: RimeSchemePackage) throws -> URL {
        let url = store.stagingRoot.appendingPathComponent(package.id, isDirectory: true)
        try writeDeployment(package, at: url)
        return url
    }
    private func writeDeployment(_ package: RimeSchemePackage, at url: URL) throws {
        let build = url.appendingPathComponent("build", isDirectory: true)
        try FileManager.default.createDirectory(at: build, withIntermediateDirectories: true)
        for schema in package.schemas {
            try Data("schema:\n  schema_id: \(schema.id)\n  name: Test\n".utf8)
                .write(to: build.appendingPathComponent("\(schema.id).schema.yaml"))
        }
    }
    private func publish(_ package: RimeSchemePackage) throws {
        try store.publish(stagedURL: stage(package), package: package)
    }
}

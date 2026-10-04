import Foundation
import RimesCore

/// The app owns writes in the shared container. The keyboard reads receipts and
/// rechecks authorization before publishing or inserting plugin output.
enum MobileOfficialPlugins {
    static func makeStore() throws -> OfficialPluginStore {
        let configuration = ConfigurationStore()
        return OfficialPluginStore(root: configuration.pluginRoot, platform: "ios",
            hostVersion: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.1.0",
            catalog: try .bundled(), legacyProfile: configuration.hasPersistedConfiguration)
    }

    /// An explicit install may replace an unreadable or obsolete receipt.
    /// Revoke it first so an older download cannot overwrite the repair.
    static func install(_ id: String, in store: OfficialPluginStore) async throws {
        if store.state(id) == nil { try store.uninstall(id) }
        try await store.install(id)
    }
}

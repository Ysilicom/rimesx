import Foundation
import RimesCore

struct KeyboardAppearanceConfiguration: Codable, Equatable {
    var layout: OrdinaryKeyboardLayout = .qwerty
    /// Pre-build-28 skin choice, read only during migration to KeyboardThemeStore.
    var skin: KeyboardSkin = .system
    var revision: UUID?
}

/// Ordinary layout preferences, independent of chord mappings, imported schemes and pet themes.
final class KeyboardAppearanceStore {
    private let file: URL
    init(root: URL? = nil) {
        let root = root ?? FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: ConfigurationStore.groupID)
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        file = root.appendingPathComponent("keyboard-appearance-v1.json")
    }
    func load() -> KeyboardAppearanceConfiguration {
        guard let data = try? Data(contentsOf: file), data.count < 4096,
              let value = try? JSONDecoder().decode(KeyboardAppearanceConfiguration.self, from: data) else { return .init() }
        return value
    }
    func save(_ value: KeyboardAppearanceConfiguration) throws {
        var value = value; value.revision = UUID()
        try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(value).write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}

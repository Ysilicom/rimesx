import Foundation
import RimesCore

struct KeyboardThemeConfiguration: Codable, Equatable {
    var selection: StatusSkin = .apple
    var revision: UUID?
}

/// A theme edit never rewrites a layout, input scheme or rotation list.
final class KeyboardThemeStore {
    private let file: URL
    init(root: URL? = nil) {
        let root = root ?? FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: ConfigurationStore.groupID)
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        file = root.appendingPathComponent("keyboard-theme-v1.json")
    }
    func load() -> KeyboardThemeConfiguration {
        guard let data = try? Data(contentsOf: file), data.count < 4096,
              var value = try? JSONDecoder().decode(KeyboardThemeConfiguration.self, from: data) else { return .init() }
        value.selection = value.selection.canonical
        return value
    }
    @discardableResult func save(_ selection: StatusSkin) throws -> KeyboardThemeConfiguration {
        let value = KeyboardThemeConfiguration(selection: selection.canonical, revision: UUID())
        try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(value).write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        return value
    }
}

extension KeyboardPreferences {
    var resolvedTheme: StatusSkin { (StatusSkin(rawValue: statusSkin) ?? .apple).canonical }
    /// Apply an explicit app choice once; otherwise preserve an existing pet during the upgrade.
    mutating func reconcileTheme(_ config: KeyboardThemeConfiguration) {
        if let revision = config.revision, revision != appliedKeyboardThemeRevision {
            statusSkin = config.selection.canonical.rawValue
            appliedKeyboardThemeRevision = revision
        } else if keyboardThemeMigrationVersion == 0 {
            if statusSkin == StatusSkin.light.rawValue || StatusSkin(rawValue: statusSkin) == nil {
                statusSkin = (keyboardSkin == .rimes ? StatusSkin.rhino : .apple).rawValue
            }
        }
        statusSkin = resolvedTheme.rawValue
        keyboardSkin = resolvedTheme.keyboardStyle
        keyboardThemeMigrationVersion = 1
    }
}

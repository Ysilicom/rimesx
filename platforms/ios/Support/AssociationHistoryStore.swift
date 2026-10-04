import Foundation
import RimesCore

/// Learned text stays in the keyboard's private container. The app sends only a
/// reset revision in its configuration; it never reads or exports the history.
final class AssociationHistoryStore {
    private let file: URL
    private let defaults: UserDefaults
    private let resetKey = "association-history-reset-v1"
    init(root: URL? = nil, defaults: UserDefaults = .standard) {
        file = (root ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0])
            .appendingPathComponent("RIMESAssociations.json")
        self.defaults = defaults
    }
    var resetRevision: UUID? { defaults.string(forKey: resetKey).flatMap(UUID.init(uuidString:)) }
    func load() -> AssociationHistory {
        guard let data = try? Data(contentsOf: file),
              let history = try? JSONDecoder().decode(AssociationHistory.self, from: data) else { return .init() }
        return history
    }
    /// A controller from before a reset must not restore its old cached history.
    @discardableResult func save(_ history: AssociationHistory, revision: UUID?) throws -> Bool {
        guard revision == resetRevision else { return false }
        try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(history).write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        var excluded = file; var values = URLResourceValues(); values.isExcludedFromBackup = true
        try? excluded.setResourceValues(values)
        return true
    }
    @discardableResult func applyReset(_ requested: UUID?) throws -> Bool {
        guard let requested, requested != resetRevision else { return false }
        do { try FileManager.default.removeItem(at: file) }
        catch let error as CocoaError where error.code == .fileNoSuchFile { }
        // A failed deletion never acknowledges the request; retry on next presentation.
        defaults.set(requested.uuidString, forKey: resetKey)
        return true
    }
}

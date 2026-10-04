import Foundation
import RimesCore

struct CustomLayoutLibrary: Codable {
    var layouts: [CustomKeyboardLayout] = []
    /// A separate, validated snapshot: editing a draft never changes a live keyboard.
    var active: CustomKeyboardLayout?
    var revision: String = UUID().uuidString
}

/// Independent of configuration-v1.json and extension preferences, including all chord settings.
final class CustomLayoutStore {
    private let root: URL
    private var file: URL { root.appendingPathComponent("keyboard-layouts-v1.json") }
    init(root: URL? = nil) {
        self.root = root ?? FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: ConfigurationStore.groupID)
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
    }
    func load() -> CustomLayoutLibrary {
        guard let data = try? Data(contentsOf: file), data.count <= 2 * 1024 * 1024,
              var library = try? JSONDecoder().decode(CustomLayoutLibrary.self, from: data),
              library.layouts.count <= 64 else { return .init() }
        library.layouts = library.layouts.filter(Self.safeDraft)
        library.active = library.active.flatMap { try? $0.validated() }
        return library
    }
    func saveDraft(_ layout: CustomKeyboardLayout) throws {
        guard Self.safeDraft(layout) else { throw CoreError.tooLarge }
        var library = load()
        if let index = library.layouts.firstIndex(where: { $0.id == layout.id }) { library.layouts[index] = layout }
        else { library.layouts.append(layout) }
        try write(library)
    }
    func apply(_ layout: CustomKeyboardLayout?) throws {
        var library = load()
        library.active = try layout?.validated()
        if let layout = library.active {
            if let index = library.layouts.firstIndex(where: { $0.id == layout.id }) { library.layouts[index] = layout }
            else { library.layouts.append(layout) }
        }
        library.revision = UUID().uuidString
        try write(library)
    }
    func remove(id: String) throws {
        var library = load(); library.layouts.removeAll { $0.id == id }
        if library.active?.id == id { library.active = nil; library.revision = UUID().uuidString }
        try write(library)
    }
    private func write(_ library: CustomLayoutLibrary) throws {
        guard library.layouts.count <= 64 else { throw CoreError.tooLarge }
        let data = try JSONEncoder().encode(library)
        guard data.count <= 2 * 1024 * 1024 else { throw CoreError.tooLarge }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        try data.write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
    private static func safeDraft(_ layout: CustomKeyboardLayout) -> Bool {
        let keys = layout.rows.flatMap { $0 }, m = layout.metrics
        return layout.formatVersion == 1 && !layout.id.isEmpty && layout.id.count <= 128
            && layout.name.count <= 60 && (1...6).contains(layout.rows.count)
            && layout.rows.allSatisfy { (1...16).contains($0.count) } && keys.count <= 80
            && Set(keys.map(\.id)).count == keys.count
            && keys.allSatisfy { !$0.id.isEmpty && $0.id.count <= 128 && $0.text.count <= 16 && $0.width.isFinite && (0.5...4).contains($0.width) }
            && m.keyHeight.isFinite && (30...72).contains(m.keyHeight)
            && m.gap.isFinite && (0...12).contains(m.gap)
            && m.padding.isFinite && (0...28).contains(m.padding)
            && m.splitGap.isFinite && (0...60).contains(m.splitGap)
    }
}

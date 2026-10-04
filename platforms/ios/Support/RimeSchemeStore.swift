import Foundation
import Darwin

struct ImportedRimeSchema: Codable, Identifiable, Hashable, Sendable {
    var id: String
    var name: String
}

struct RimeSchemePackage: Codable, Identifiable, Sendable {
    var id: String
    var name: String
    var schemas: [ImportedRimeSchema]
    var importedAt: Date
    var sourceDigest: String
    var warnings: [String]
}

struct RimeSchemeSelection: Codable, Equatable, Sendable {
    var packageID: String
    var schemaID: String
}

struct RimeSchemeLibrary: Codable, Sendable {
    var packages: [RimeSchemePackage] = []
    var active: RimeSchemeSelection?
    var revision: String = UUID().uuidString
}

/// Local menu choices survive keyboard presentations until the main app makes
/// another explicit selection. A missing choice differs from choosing built-in.
struct KeyboardRimeSchemeChoice: Codable {
    var appRevision: String
    var selection: RimeSchemeSelection?
    var englishInput: Bool = false
}

enum RimeSchemeError: LocalizedError {
    case invalid(String)
    var errorDescription: String? {
        switch self { case .invalid(let message): return message }
    }
}

/// Only the containing app publishes packages and requests a default selection.
/// The extension keeps its own selection override and never needs group write access.
final class RimeSchemeStore: @unchecked Sendable {
    let root: URL
    var stagingRoot: URL { root.appendingPathComponent(".staging", isDirectory: true) }
    private var file: URL { root.appendingPathComponent("library-v1.json") }
    init(root: URL? = nil) {
        let group = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: ConfigurationStore.groupID)
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        self.root = root ?? group.appendingPathComponent("Library/Application Support/RimeSchemes", isDirectory: true)
    }
    func packageURL(id: String) -> URL {
        root.appendingPathComponent("packages", isDirectory: true).appendingPathComponent(id, isDirectory: true)
    }
    func load() -> RimeSchemeLibrary {
        guard let data = try? Data(contentsOf: file), data.count <= 1024 * 1024,
              var library = try? JSONDecoder().decode(RimeSchemeLibrary.self, from: data),
              library.packages.count <= 32 else { return .init() }
        library.packages = library.packages.filter(Self.valid)
        if let active = library.active, !library.packages.contains(where: { $0.id == active.packageID && $0.schemas.contains(where: { $0.id == active.schemaID }) }) { library.active = nil }
        return library
    }
    func resolve(_ selection: RimeSchemeSelection) -> (package: RimeSchemePackage, schema: ImportedRimeSchema, resources: URL)? {
        guard let package = load().packages.first(where: { $0.id == selection.packageID }),
              let schema = package.schemas.first(where: { $0.id == selection.schemaID }) else { return nil }
        let url = packageURL(id: package.id)
        guard Self.isCompiledFile(url.appendingPathComponent("build/\(schema.id).schema.yaml")) else { return nil }
        return (package, schema, url)
    }
    /// Deployment is complete before this move. A partial directory is never selected.
    func publish(stagedURL: URL, package: RimeSchemePackage) throws {
        guard Self.valid(package), stagedURL.deletingLastPathComponent().standardizedFileURL == stagingRoot.standardizedFileURL,
              stagedURL.lastPathComponent == package.id else { throw RimeSchemeError.invalid("方案暂存目录无效。") }
        for schema in package.schemas {
            guard Self.isCompiledFile(stagedURL.appendingPathComponent("build/\(schema.id).schema.yaml")) else {
                throw RimeSchemeError.invalid("方案尚未部署完成：\(schema.name)")
            }
        }
        try withWriteLock {
            var library = load()
            guard library.packages.count < 32, !library.packages.contains(where: { $0.id == package.id }) else { throw RimeSchemeError.invalid("已安装的方案包数量达到上限。") }
            let target = packageURL(id: package.id)
            try FileManager.default.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
            try FileManager.default.moveItem(at: stagedURL, to: target)
            do {
                library.packages.append(package)
                // Installing is not activating: preserve the current selection and revision.
                try write(library)
            } catch { try? FileManager.default.removeItem(at: target); throw error }
        }
    }
    func activate(_ selection: RimeSchemeSelection?) throws {
        try withWriteLock {
            if let selection, resolve(selection) == nil { throw RimeSchemeError.invalid("找不到已部署的输入方案，请重新导入。") }
            var library = load(); library.active = selection; library.revision = UUID().uuidString
            try write(library)
        }
    }
    static func safeSchemaID(_ id: String) -> Bool {
        !id.isEmpty && id.utf8.count <= 120 && id != "." && id != ".."
            && id.unicodeScalars.allSatisfy { CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.-").contains($0) }
    }
    private static func isCompiledFile(_ url: URL) -> Bool {
        guard let values = try? url.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey]) else { return false }
        return values.isRegularFile == true && values.isSymbolicLink != true && (values.fileSize ?? 0) > 0
    }
    private static func valid(_ package: RimeSchemePackage) -> Bool {
        UUID(uuidString: package.id) != nil && !package.name.isEmpty && package.name.count <= 200
            && (1...64).contains(package.schemas.count) && Set(package.schemas.map(\.id)).count == package.schemas.count
            && package.schemas.allSatisfy { safeSchemaID($0.id) && !$0.name.isEmpty && $0.name.count <= 200 }
            && package.warnings.count <= 512 && package.warnings.allSatisfy { $0.count <= 4000 }
    }
    private func write(_ library: RimeSchemeLibrary) throws {
        let data = try JSONEncoder().encode(library)
        guard data.count <= 1024 * 1024 else { throw RimeSchemeError.invalid("方案目录过大。") }
        try data.write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
    private func withWriteLock(_ action: () throws -> Void) throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true, attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
        let fd = open(root.appendingPathComponent("library.lock").path, O_CREAT | O_RDWR, S_IRUSR | S_IWUSR)
        guard fd >= 0 else { throw RimeSchemeError.invalid("无法锁定方案目录。") }
        defer { close(fd) }
        guard flock(fd, LOCK_EX) == 0 else { throw RimeSchemeError.invalid("方案目录正忙。") }
        defer { flock(fd, LOCK_UN) }
        try action()
    }
}

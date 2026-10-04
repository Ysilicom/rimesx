import Foundation

/// A downloaded official package supplies bounded data to a known interpreter.
/// It never selects an executable, network endpoint, credential, or input target.
public struct OfficialPluginPackage: Codable, Equatable {
    public struct Platform: Codable, Equatable {
        public let legacyID: String
        public let distribution: String
    }

    public struct Contribution: Codable, Equatable {
        public let type: String
        public let instructions: [String: String]
    }

    public static let maximumBytes = 256 * 1024
    public let schemaVersion: Int
    public let sdkVersion: Int
    public let id: String
    public let version: String
    public let minimumHostVersion: String
    public let kind: String
    public let runtime: String
    public let nameZH: String
    public let nameEN: String
    public let summaryZH: String
    public let summaryEN: String
    public let platforms: [String: Platform]
    public let capabilities: [String]
    public let contribution: Contribution
    public let license: String
    public let licenseText: String
    public let notice: String

    /// The caller verifies the catalog's SHA-256 before invoking this parser.
    /// Expected identity comes from that trusted catalog, never from the file.
    public static func validated(_ data: Data, expectedID: String,
                                 expectedVersion: String, platform: String,
                                 hostVersion: String) throws -> Self {
        guard data.count <= maximumBytes else { throw PluginPackageError.invalid }
        let package: Self
        do { package = try JSONDecoder().decode(Self.self, from: data) }
        catch { throw PluginPackageError.invalid }
        guard package.id == expectedID, package.version == expectedVersion,
              package.id.range(of: #"^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+$"#,
                               options: .regularExpression) != nil,
              PluginPackageVersion(package.version) != nil,
              let minimum = PluginPackageVersion(package.minimumHostVersion),
              let current = PluginPackageVersion(hostVersion) else {
            throw PluginPackageError.invalid
        }
        guard package.schemaVersion == 2, package.sdkVersion == 1,
              package.runtime == "host-interpreted",
              package.kind == "buffer",
              package.contribution.type == "ai.prompt.v1",
              package.capabilities == ["buffer.read", "ai.generate"] else {
            throw PluginPackageError.unsupported
        }
        let supported = Set(["macos", "ios", "android", "windows"])
        guard !package.platforms.isEmpty,
              Set(package.platforms.keys).isSubset(of: supported),
              package.platforms[platform] != nil else {
            throw PluginPackageError.unsupported
        }
        guard minimum <= current else { throw PluginPackageError.hostTooOld }
        for adapter in package.platforms.values {
            guard validText(adapter.legacyID, maximum: 128),
                  ["bundled", "download"].contains(adapter.distribution) else {
                throw PluginPackageError.invalid
            }
        }
        guard [package.nameZH, package.nameEN, package.summaryZH,
               package.summaryEN, package.notice].allSatisfy({ validText($0, maximum: 4096) }),
              package.license == "Apache-2.0",
              validText(package.licenseText, maximum: 32768),
              package.contribution.instructions["default"] != nil,
              Set(package.contribution.instructions.keys).isSubset(of: ["default", "image"]),
              package.contribution.instructions.values.allSatisfy({ validText($0, maximum: 32768) }) else {
            throw PluginPackageError.invalid
        }
        return package
    }

    public func instruction(mode: String = "default") throws -> String {
        guard let instruction = contribution.instructions[mode] else {
            throw PluginPackageError.unsupported
        }
        return instruction
    }

    private static func validText(_ value: String, maximum: Int) -> Bool {
        !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !value.contains("\0") && value.utf8.count <= maximum
    }
}

public enum PluginPackageError: Error, Equatable {
    case invalid, unsupported, hostTooOld
}

public struct PluginPackageVersion: Comparable {
    private let components: [Int]
    public init?(_ value: String) {
        guard value.range(of: #"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$"#,
                          options: .regularExpression) != nil else { return nil }
        let parsed = value.split(separator: ".").compactMap { Int($0) }
        guard parsed.count == 3 else { return nil }
        components = parsed
    }
    public static func < (lhs: Self, rhs: Self) -> Bool {
        lhs.components.lexicographicallyPrecedes(rhs.components)
    }
}

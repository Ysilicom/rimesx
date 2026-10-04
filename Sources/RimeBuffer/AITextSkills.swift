import Foundation

extension Notification.Name {
    static let aiTextSkillSelectionDidChange = Notification.Name(
        "RimeBuffer.AITextSkillSelection.didChange"
    )
}

/// A skill is a per-request capability, never an ambient user-home directory.
/// The registry is keyed by provider so another adapter can add a concrete
/// mount later without changing the toolbar or persisted selection shape.
enum AITextSkillKind: String, Codable, Equatable {
    case imagegen

    var title: String {
        switch self {
        case .imagegen: return "图片 · imagegen"
        }
    }
}

enum AITextSkillCatalog {
    static func available(for provider: AITextProviderKind) -> [AITextSkillKind] {
        switch provider {
        case .codexCLI: return [.imagegen]
        case .claudeCodeCLI, .openAICompatible: return []
        }
    }

    static func supports(_ skill: AITextSkillKind,
                         provider: AITextProviderKind) -> Bool {
        available(for: provider).contains(skill)
    }
}

final class AITextSkillSelectionStore {
    static let shared = AITextSkillSelectionStore()

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func selected(for provider: AITextProviderKind) -> AITextSkillKind? {
        let key = "\(RimesIdentity.preferenceKeyPrefix)AIText.skill.\(provider.rawValue).v1"
        guard let raw = defaults.string(forKey: key),
              let skill = AITextSkillKind(rawValue: raw),
              AITextSkillCatalog.supports(skill, provider: provider) else {
            return nil
        }
        return skill
    }

    func select(_ skill: AITextSkillKind?,
                for provider: AITextProviderKind) {
        if let skill, !AITextSkillCatalog.supports(skill, provider: provider) {
            return
        }
        let key = "\(RimesIdentity.preferenceKeyPrefix)AIText.skill.\(provider.rawValue).v1"
        guard selected(for: provider) != skill else { return }
        defaults.set(skill?.rawValue, forKey: key)
        NotificationCenter.default.post(name: .aiTextSkillSelectionDidChange,
                                        object: self)
    }
}

struct AITextMountedSkill: Equatable {
    let name: String
    let path: String
}

enum AITextSkillMounting {
    static func mount(_ skill: AITextSkillKind,
                      for provider: AITextProviderKind,
                      in workspaceURL: URL) throws -> AITextMountedSkill {
        guard AITextSkillCatalog.supports(skill, provider: provider) else {
            throw AITextProviderError.unavailable("该生成服务不支持所选 Skill")
        }
        switch skill {
        case .imagegen:
            guard let source = Bundle.module.url(forResource: "imagegen",
                                                 withExtension: nil,
                                                 subdirectory: "Skills") else {
                throw AITextProviderError.unavailable("imagegen Skill 未随应用安装")
            }
            let destination = workspaceURL.appendingPathComponent(
                "skills/imagegen", isDirectory: true
            )
            try FileManager.default.createDirectory(
                at: destination.deletingLastPathComponent(),
                withIntermediateDirectories: false,
                attributes: [.posixPermissions: 0o700]
            )
            try FileManager.default.copyItem(at: source, to: destination)
            let manifest = destination.appendingPathComponent("SKILL.md")
            guard FileManager.default.fileExists(atPath: manifest.path) else {
                throw AITextProviderError.unavailable("imagegen Skill 不完整")
            }
            return AITextMountedSkill(name: "imagegen", path: manifest.path)
        }
    }
}

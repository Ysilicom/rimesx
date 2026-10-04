import Foundation

public enum ChordLayout: String, Codable, CaseIterable { case orthogonal, splitOrthogonal }
public enum HapticStrength: String, Codable, CaseIterable { case light, strong, strongest }
public enum OrdinaryKeyboardLayout: String, Codable, CaseIterable { case qwerty, nineKey }
public enum KeyboardSkin: String, Codable, CaseIterable { case system, rimes }

/// Extension-private preferences work even when shared-container writes are forbidden.
public struct KeyboardPreferences: Codable {
    public var scheme: InputScheme = .pinyin
    public var appliedAppSelection: UUID?
    public var initialized = false
    public var haptics = true
    public var keySounds = true
    public var hapticStrength: HapticStrength = .light
    public var traditional = false
    public var chordLayout: ChordLayout = .orthogonal
    public var ordinaryLayout: OrdinaryKeyboardLayout = .qwerty
    public var keyboardSkin: KeyboardSkin = .system
    public var appliedKeyboardAppearanceRevision: UUID?
    public var appliedKeyboardThemeRevision: UUID?
    public var keyboardThemeMigrationVersion = 0
    public var overriddenCustomLayoutRevision: String?
    public var lastChineseScheme: InputScheme = .pinyin
    public var englishInput = false
    public var sourceLanguage = "zh-Hans"
    public var targetLanguage = "en"
    public var poem = PoemOptions()
    /// Read finished translation blocks aloud. Off by default.
    public var speakTranslation = false
    /// Pet and keyboard theme identifier; the iOS palette migrates legacy values.
    public var statusSkin = "light"
    public var art = TextArtOptions()
    /// Status-light looks a tap rotates through; empty means all.
    public var statusSkinRotation: [String] = []
    /// Last app edit of the rotation already applied here.
    public var appliedSkinRevision: UUID?
    /// Thinking depth asked of AI plugins.
    public var thinking: AIThinking = .minimal
    public init() {}
    private enum CodingKeys: String, CodingKey {
        case scheme, appliedAppSelection, initialized, haptics, keySounds, traditional, sourceLanguage, targetLanguage
        case chordLayout, hapticStrength, lastChineseScheme, englishInput, poem, speakTranslation, statusSkin, art, statusSkinRotation, appliedSkinRevision, thinking
        case ordinaryLayout, keyboardSkin, appliedKeyboardAppearanceRevision, overriddenCustomLayoutRevision
        case appliedKeyboardThemeRevision, keyboardThemeMigrationVersion
    }
    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        scheme = try values.decodeIfPresent(InputScheme.self, forKey: .scheme) ?? .pinyin
        appliedAppSelection = try values.decodeIfPresent(UUID.self, forKey: .appliedAppSelection)
        initialized = try values.decodeIfPresent(Bool.self, forKey: .initialized) ?? false
        haptics = try values.decodeIfPresent(Bool.self, forKey: .haptics) ?? true
        keySounds = try values.decodeIfPresent(Bool.self, forKey: .keySounds) ?? true
        traditional = try values.decodeIfPresent(Bool.self, forKey: .traditional) ?? false
        chordLayout = (try? values.decode(ChordLayout.self, forKey: .chordLayout)) ?? .orthogonal
        ordinaryLayout = (try? values.decode(OrdinaryKeyboardLayout.self, forKey: .ordinaryLayout)) ?? .qwerty
        keyboardSkin = (try? values.decode(KeyboardSkin.self, forKey: .keyboardSkin)) ?? .system
        appliedKeyboardAppearanceRevision = try? values.decode(UUID.self, forKey: .appliedKeyboardAppearanceRevision)
        appliedKeyboardThemeRevision = try? values.decode(UUID.self, forKey: .appliedKeyboardThemeRevision)
        keyboardThemeMigrationVersion = (try? values.decode(Int.self, forKey: .keyboardThemeMigrationVersion)) ?? 0
        overriddenCustomLayoutRevision = try? values.decode(String.self, forKey: .overriddenCustomLayoutRevision)
        hapticStrength = (try? values.decode(HapticStrength.self, forKey: .hapticStrength)) ?? .light
        lastChineseScheme = (try? values.decode(InputScheme.self, forKey: .lastChineseScheme)) ?? (scheme == .english ? .pinyin : scheme)
        if lastChineseScheme == .english { lastChineseScheme = .pinyin }
        englishInput = try values.decodeIfPresent(Bool.self, forKey: .englishInput) ?? false
        sourceLanguage = try values.decodeIfPresent(String.self, forKey: .sourceLanguage) ?? "zh-Hans"
        targetLanguage = try values.decodeIfPresent(String.self, forKey: .targetLanguage) ?? "en"
        poem = (try? values.decode(PoemOptions.self, forKey: .poem)) ?? PoemOptions()
        speakTranslation = (try? values.decode(Bool.self, forKey: .speakTranslation)) ?? false
        statusSkin = (try? values.decode(String.self, forKey: .statusSkin)) ?? "light"
        art = (try? values.decode(TextArtOptions.self, forKey: .art)) ?? TextArtOptions()
        statusSkinRotation = (try? values.decode([String].self, forKey: .statusSkinRotation)) ?? []
        appliedSkinRevision = try? values.decode(UUID.self, forKey: .appliedSkinRevision)
        thinking = (try? values.decode(AIThinking.self, forKey: .thinking)) ?? .minimal
    }
    public mutating func reconcile(scheme appScheme: InputScheme, revision: UUID?) {
        if !initialized || (revision != nil && revision != appliedAppSelection) {
            select(appScheme); appliedAppSelection = revision
        }
    }
    public mutating func select(_ value: InputScheme) {
        scheme = value; initialized = true; englishInput = false
        if value != .english { lastChineseScheme = value }
    }
    public mutating func toggleLanguage() {
        if scheme == .english { select(lastChineseScheme) }
        else { lastChineseScheme = scheme; englishInput.toggle() }
    }
}

public enum KeyFeedback: Equatable { case press, selection, commit }
public struct FeedbackGate {
    private var lastTime: TimeInterval = -.infinity
    private var combination: String?
    public init() {}
    public mutating func reset() { combination = nil }
    public mutating func accept(_ event: KeyFeedback, combination next: String? = nil, at time: TimeInterval) -> Bool {
        if event == .selection {
            guard next != combination else { return false }
            combination = next
            guard next != nil else { return false }
        }
        guard time - lastTime >= 0.035 else { return false }
        lastTime = time; return true
    }
}

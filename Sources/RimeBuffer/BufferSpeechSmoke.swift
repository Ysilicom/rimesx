import Foundation

/// Pure checks for read-aloud. It never plays audio.
func runBufferSpeechSmokeTest() -> Bool {
    func fail(_ message: String) -> Bool {
        print("FAILED: buffer speech \(message)")
        return false
    }
    let voices = ["zh-CN", "zh-TW", "zh-HK", "en-US", "en_GB", "ja-JP", "fr-FR", "fr-CA"]
    func resolve(_ id: String, _ installed: [String] = voices, region: String? = "US") -> String? {
        BufferSpeechVoiceResolver.voiceLanguage(for: id, installed: installed, userRegion: region)
    }

    guard resolve("zh-Hans") == "zh-CN",
          resolve("zh-Hans", region: "TW") == "zh-CN",
          resolve("zh-Hant") == "zh-TW",
          resolve("zh-Hant", region: "HK") == "zh-HK",
          resolve("zh-Hant", ["zh-CN", "zh-HK"]) == "zh-HK" else {
        return fail("chinese scripts pick matching regions")
    }
    guard resolve("zh-Hant", ["zh-CN", "en-US"]) == nil,
          resolve("zh-Hans", ["zh-TW"]) == nil else {
        return fail("a script never falls back to the other script")
    }
    guard resolve("en") == "en-US",
          resolve("en", region: "GB") == "en-GB",
          resolve("en", region: "CN") == "en-US",
          resolve("ja") == "ja-JP",
          resolve("fr-CA") == "fr-CA",
          resolve("fr", region: "CA") == "fr-CA",
          resolve("fr") == "fr-FR" else {
        return fail("language and region preference")
    }
    guard resolve("ko") == nil,
          resolve("auto") == nil,
          resolve("") == nil else {
        return fail("missing voices are reported, never substituted")
    }

    // The switch is off by default and announces every change.
    let suite = "RimeBuffer.BufferSpeechSmoke.\(UUID().uuidString)"
    guard let defaults = UserDefaults(suiteName: suite) else { return fail("defaults") }
    defer { defaults.removePersistentDomain(forName: suite) }
    let center = NotificationCenter()
    var changes = 0
    let observer = center.addObserver(forName: .bufferSpeechDidChange, object: nil,
                                      queue: nil) { _ in changes += 1 }
    defer { center.removeObserver(observer) }
    let preferences = BufferSpeechPreferences(defaults: defaults, notificationCenter: center)
    guard !preferences.isEnabled else { return fail("switch defaults to off") }
    preferences.isEnabled = true
    preferences.isEnabled = true
    guard preferences.isEnabled, changes == 1,
          BufferSpeechPreferences(defaults: defaults, notificationCenter: center).isEnabled
    else { return fail("switch persists and notifies once per change") }
    preferences.isEnabled = false
    guard !preferences.isEnabled, changes == 2 else { return fail("switch turns off") }

    let reader = BufferSpeechReader.shared
    guard reader.speak("   ", languageID: "en") == .empty,
          !reader.isSpeaking else {
        return fail("empty text never starts speech")
    }
    reader.stop()
    guard !reader.isSpeaking else { return fail("stop settles state") }

    print("PASS: buffer speech voice resolution, switch, empty guard")
    return true
}

#if DEBUG
import Foundation
import UIKit
import RimesCore

/// Explicit development diagnostic using the shipped engine and data. No customer content.
@MainActor enum DevelopmentSmoke {
    static func runIfRequested() async {
        if ProcessInfo.processInfo.arguments.contains("--shared-storage-prepare") { sharedStorageSmoke(prepare: true) }
        if ProcessInfo.processInfo.arguments.contains("--shared-storage-verify") { sharedStorageSmoke(prepare: false) }
        if ProcessInfo.processInfo.arguments.contains("--typing-card-smoke") { await typingCardSmoke() }
        if ProcessInfo.processInfo.arguments.contains("--layout-config-backup") { backupLayoutConfiguration() }
        if let index = ProcessInfo.processInfo.arguments.firstIndex(of: "--layout-import-smoke"),
           ProcessInfo.processInfo.arguments.indices.contains(index + 1) {
            layoutImportSmoke(path: ProcessInfo.processInfo.arguments[index + 1])
        }
        if let index = ProcessInfo.processInfo.arguments.firstIndex(of: "--scheme-import-smoke"),
           ProcessInfo.processInfo.arguments.indices.contains(index + 1) {
            await schemeImportSmoke(path: ProcessInfo.processInfo.arguments[index + 1])
        }
        if ProcessInfo.processInfo.arguments.contains("--translation-smoke") { await translationSmoke() }
        guard ProcessInfo.processInfo.arguments.contains("--engine-smoke") else { return }
        var checks: [[String:Any]] = []
        let start = ProcessInfo.processInfo.systemUptime
        let engine = MobileEngine()
        let coldMs = (ProcessInfo.processInfo.systemUptime-start)*1000
        for (schema,code,expected) in [("rimes_pinyin","nihao","你好"),("rimes_ziranma","nihk","你好"),("rimes_wubi","wq","你")] {
            let selected = engine.select(schema:schema)
            var state = EngineSnapshot(), times = [Double]()
            for c in code.unicodeScalars {
                let t = ProcessInfo.processInfo.systemUptime; state = engine.process(key:Int32(c.value)); times.append((ProcessInfo.processInfo.systemUptime-t)*1000)
            }
            let index = state.candidates.firstIndex(of:expected)
            let committed = index.map { engine.candidate($0).commit } ?? ""
            checks.append(["schema":schema,"passed":selected && committed == expected,"keyMilliseconds":times])
            engine.clear()
        }
        let id = UUID(), keychain = KeychainStore()
        do { try keychain.save("development-only",id:id); let good = try keychain.read(id)=="development-only"; try keychain.delete(id); checks.append(["keychain":true,"passed":good]) }
        catch { checks.append(["keychain":true,"passed":false,"osStatus":(error as NSError).code]) }
        #if targetEnvironment(simulator)
        let environment = "iOS simulator, not physical-device acceptance"
        #else
        let environment = "iOS device engine smoke, not full keyboard acceptance"
        #endif
        let report: [String:Any] = ["checks":checks,"engineInitializationMilliseconds":coldMs,"environment":environment,"osVersion":UIDevice.current.systemVersion,"deviceModel":UIDevice.current.model,"allPassed":checks.allSatisfy { $0["passed"] as? Bool == true }]
        do {
            let url = FileManager.default.urls(for:.documentDirectory,in:.userDomainMask)[0].appendingPathComponent("development-smoke.json")
            try JSONSerialization.data(withJSONObject:report,options:[.prettyPrinted,.sortedKeys]).write(to:url,options:.atomic)
        } catch { assertionFailure("Could not write smoke report") }
    }
    private static func sharedStorageSmoke(prepare: Bool) {
        var report: [String: Any]
        do {
            if prepare { try SharedStorageDeviceSmoke.prepare(); report = ["prepared": true] }
            else { report = try SharedStorageDeviceSmoke.verify() }
        } catch { report = ["allPassed": false, "error": error.localizedDescription] }
        let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("shared-storage-smoke.json")
        try? JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]).write(to: url, options: .atomic)
    }
    private static func typingCardSmoke() async {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        var report: [String: Any] = ["build": Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") ?? "", "scope": "synthetic stats only; no chat message sent"]
        do {
            let png = try TypingStatsCard.render(.init(characters: 8, average: 68, peak: 90, codeLength: 2.38, keysPerSecond: 2.7), chinese: true)
            let store = TypingCardStore(directory: documents.appendingPathComponent("typing-card-smoke"))
            try store.save(png); report["passed"] = try store.load() == png; report["pngBytes"] = png.count
            if ProcessInfo.processInfo.arguments.contains("--typing-card-preview-sample") { try TypingCardStore().save(png) }
            if ProcessInfo.processInfo.arguments.contains("--typing-card-photos-smoke") {
                do { try await TypingCardPhotos().save(png); report["savedToPhotos"] = true }
                catch { report["savedToPhotos"] = false; report["photoError"] = error.localizedDescription }
            }
        } catch { report["passed"] = false; report["error"] = error.localizedDescription }
        try? JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]).write(to: documents.appendingPathComponent("typing-card-smoke.json"), options: .atomic)
    }
    private static func backupLayoutConfiguration() {
        guard let group = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: ConfigurationStore.groupID) else { return }
        let destination = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("layout-configuration-backup.json")
        // Raw, read-only snapshot: do not normalize or resave the existing chord configuration.
        if let data = try? Data(contentsOf: group.appendingPathComponent("configuration-v1.json")) { try? data.write(to: destination, options: [.atomic, .completeFileProtection]) }
    }
    private static func layoutImportSmoke(path: String) {
        var report: [String: Any] = ["environment": "main-app layout import; no configuration applied"]
        do {
            let url = path.hasPrefix("/") ? URL(fileURLWithPath: path)
                : FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent(path)
            let result = try CustomLayoutImportService.inspect(url: url)
            report["passed"] = !result.layouts.isEmpty
            report["title"] = result.title; report["details"] = result.details; report["warnings"] = result.warnings
            report["layouts"] = result.layouts.map { layout -> [String: Any] in
                var item: [String: Any] = ["name": layout.name, "rowCounts": layout.rows.map(\.count)]
                do { _ = try layout.validated(); item["readyToApply"] = true }
                catch { item["readyToApply"] = false; item["needsEditing"] = error.localizedDescription }
                return item
            }
            if ProcessInfo.processInfo.arguments.contains("--layout-import-draft"), let draft = result.layouts.first {
                try CustomLayoutStore().saveDraft(draft)
                report["savedDraft"] = draft.name
                report["activeLayoutChanged"] = false
            }
        } catch { report["passed"] = false; report["error"] = error.localizedDescription }
        let destination = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("layout-import-smoke.json")
        try? JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]).write(to: destination, options: .atomic)
    }
    private static func schemeImportSmoke(path: String) async {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let destination = documents.appendingPathComponent("scheme-import-smoke.json")
        let source = path.hasPrefix("/") ? URL(fileURLWithPath: path) : documents.appendingPathComponent(path)
        let start = ProcessInfo.processInfo.systemUptime
        var report: [String: Any] = ["environment": "main-app scheme deployment and engine; not keyboard host acceptance", "build": Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") ?? ""]
        let store = RimeSchemeStore(), previous = RimeSchemeStore().load().active
        do {
            let review = try await Task.detached { try RimeSchemeImportService.inspect(url: source) }.value
            report["sourceDigest"] = review.archiveSHA256
            let package: RimeSchemePackage
            if let existing = store.load().packages.first(where: { $0.sourceDigest == review.archiveSHA256 && $0.schemas.contains(where: { $0.id == "xmjd6" }) }) {
                package = existing
            } else {
                package = try await RimeSchemeInstaller.install(review: review, selectedSchemaIDs: ["xmjd6"]) { stage in
                    try? Data(stage.utf8).write(to: documents.appendingPathComponent("scheme-import-progress.txt"), options: .atomic)
                }
            }
            report["packageID"] = package.id; report["warnings"] = package.warnings
            let selection = RimeSchemeSelection(packageID: package.id, schemaID: "xmjd6")
            let engine = MobileEngine()
            guard engine.selectImported(selection) else { throw RimeSchemeError.invalid(engine.lastError.isEmpty ? "星猫无法载入" : engine.lastError) }
            var checks: [[String: Any]] = []
            for (code, expected) in [("nkhz", "你好"), ("fygl", "中国"), ("ekjd", "世界"), ("ia", "冂")] {
                engine.clear()
                var state = EngineSnapshot(), committed = "", errors = [String]()
                for scalar in code.unicodeScalars {
                    state = engine.process(key: Int32(scalar.value)); committed += state.commit
                    if !engine.lastError.isEmpty { errors.append(engine.lastError) }
                }
                let index = state.candidates.firstIndex(of: expected)
                if let index { committed += engine.candidate(index).commit }
                checks.append(["code": code, "expected": expected, "candidates": Array(state.candidates.prefix(5)), "commit": committed, "errors": errors,
                               "passed": committed == expected && errors.isEmpty])
            }
            engine.clear()
            var commits = "", topupState = EngineSnapshot(), topupErrors = [String]()
            for scalar in "nkhzn".unicodeScalars {
                topupState = engine.process(key: Int32(scalar.value)); commits += topupState.commit
                if !engine.lastError.isEmpty { topupErrors.append(engine.lastError) }
            }
            checks.append(["code": "nkhzn", "purpose": "topup commits previous word and starts next code", "commit": commits, "remainingCode": engine.rawInput, "errors": topupErrors,
                           "passed": commits == "你好" && engine.rawInput == "n" && topupErrors.isEmpty])
            for code in ["ihello", "iabc"] {
                engine.clear()
                var errors = [String]()
                for scalar in code.unicodeScalars {
                    _ = engine.process(key: Int32(scalar.value))
                    if !engine.lastError.isEmpty { errors.append(engine.lastError) }
                }
                checks.append(["code": code, "purpose": "English prefix Lua source dictionary access", "errors": errors, "passed": errors.isEmpty])
            }
            engine.clear()
            let restored = engine.select(schema: "rimes_ziranma")
            var restoredState = EngineSnapshot()
            for scalar in "nihk".unicodeScalars { restoredState = engine.process(key: Int32(scalar.value)) }
            checks.append(["purpose": "restore bundled chord engine", "passed": restored && restoredState.candidates.contains("你好")])
            engine.clear()
            report["checks"] = checks
            report["passed"] = checks.allSatisfy { $0["passed"] as? Bool == true }
            if ProcessInfo.processInfo.arguments.contains("--scheme-import-activate"), report["passed"] as? Bool == true { try store.activate(selection) }
            report["selectionUnchanged"] = store.load().active == previous
        } catch { report["passed"] = false; report["error"] = error.localizedDescription }
        report["elapsedSeconds"] = ProcessInfo.processInfo.systemUptime - start
        try? JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]).write(to: destination, options: .atomic)
    }
    private static func translationSmoke() async {
        let plugin = AppleTranslationPlugin()
        let revision = UUID()
        let request = BufferPluginRequest(source: "你好，世界！", revision: revision, options: ["source": "zh-Hans", "target": "en"])
        let start = ProcessInfo.processInfo.systemUptime
        var report: [String: Any] = ["environment": "main-app Apple Translation smoke; not keyboard extension acceptance", "build": Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") ?? "", "osVersion": UIDevice.current.systemVersion]
        switch await plugin.availability(for: request) {
        case .ready:
            do {
                let result = try await plugin.execute(request) { _ in }
                report["passed"] = !result.text.isEmpty && result.revision == revision
                report["syntheticTranslation"] = result.text
            } catch { report["passed"] = false; report["error"] = error.localizedDescription }
        case .unavailable(let reason): report["passed"] = false; report["unavailable"] = reason
        }
        report["elapsedMilliseconds"] = (ProcessInfo.processInfo.systemUptime - start) * 1000
        let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("translation-smoke.json")
        try? JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]).write(to: url, options: .atomic)
    }

}
#endif

import AppKit
import Foundation

private final class PluginConfigurationNotificationProbe:
    @unchecked Sendable {
    private let lock = NSLock()
    private(set) var count = 0
    private(set) var allRedacted = true

    func record(_ userInfo: [AnyHashable: Any]) {
        let allowedKeys: Set<AnyHashable> = [
            PluginConfigurationNotificationKey.pluginID,
            PluginConfigurationNotificationKey.changedFieldIDs,
        ]
        let isRedacted = Set(userInfo.keys) == allowedKeys
            && userInfo[
                PluginConfigurationNotificationKey.pluginID
            ] is String
            && userInfo[
                PluginConfigurationNotificationKey.changedFieldIDs
            ] is [String]
        lock.lock()
        count += 1
        allRedacted = allRedacted && isRedacted
        lock.unlock()
    }

    var result: (count: Int, allRedacted: Bool) {
        lock.lock()
        defer { lock.unlock() }
        return (count, allRedacted)
    }
}

func runPluginConfigurationSmokeTest() -> Bool {
    func fail(_ message: String) -> Bool {
        print("FAILED: plugin configuration \(message)")
        return false
    }

    let suiteName = "\(RimesIdentity.preferenceKeyPrefix)PluginConfigurationSmoke.\(UUID().uuidString)"
    guard let defaults = UserDefaults(suiteName: suiteName) else {
        return fail("defaults suite")
    }
    defaults.removePersistentDomain(forName: suiteName)
    defer { defaults.removePersistentDomain(forName: suiteName) }

    let center = NotificationCenter()
    let selectionStore = AITextConnectorSelectionStore(defaults: defaults)
    let notificationProbe = PluginConfigurationNotificationProbe()
    let observer = center.addObserver(
        forName: .pluginConfigurationDidChange,
        object: nil,
        queue: nil
    ) { notification in
        notificationProbe.record(notification.userInfo ?? [:])
    }
    defer { center.removeObserver(observer) }

    let privateRoot = FileManager.default.temporaryDirectory
        .appendingPathComponent(
            "\(RimesIdentity.preferenceKeyPrefix)PluginConfigurationSmoke.\(UUID().uuidString)",
            isDirectory: true
        )
    do {
        try FileManager.default.createDirectory(
            at: privateRoot,
            withIntermediateDirectories: false,
            attributes: [.posixPermissions: 0o755]
        )
    } catch {
        return fail("private root")
    }
    defer { try? FileManager.default.removeItem(at: privateRoot) }

    do {
        // CLI channels expose request-local model and effort choices. The API
        // channel continues to take its model from the Provider route.
        for kind in [AITextProviderKind.codexCLI, .claudeCodeCLI] {
            let defaultModel = kind == .codexCLI
                ? "gpt-6-sol" : "claude-opus-5-5"
            let model = try PluginConfigurationCatalog.makeAIChannelModel(
                kind: kind, defaults: defaults, notificationCenter: center
            )
            let initial = try model.load()
            guard initial.string(AIChannelPluginConfigurationFieldID.model)
                    == defaultModel,
                  initial.string(AIChannelPluginConfigurationFieldID.effort)
                    == "medium" else {
                return fail("AI channel defaults")
            }
            let defaultSelection = try AITextGenerationPreferenceStore(defaults: defaults)
                .channelSelection(
                    connectorKind: kind, configurationDefaults: defaults
                )
            guard defaultSelection.modelID == defaultModel,
                  defaultSelection.reasoningEffort == "medium" else {
                return fail("AI channel default request")
            }
            var selected = initial
            selected[AIChannelPluginConfigurationFieldID.model] = .string(
                kind == .codexCLI ? "gpt-6-astra" : "sonnet"
            )
            selected[AIChannelPluginConfigurationFieldID.effort] = .string("high")
            try model.save(selected)
            let settings = try PluginConfigurationCatalog.aiChannelSettings(
                kind: kind, defaults: defaults
            )
            guard settings.modelID == (kind == .codexCLI ? "gpt-6-astra" : "sonnet"),
                  settings.effort == "high" else {
                return fail("AI channel setting round trip")
            }
            let selection = try AITextGenerationPreferenceStore(defaults: defaults)
                .channelSelection(
                    connectorKind: kind, configurationDefaults: defaults
                )
            guard selection.modelID == settings.modelID,
                  selection.reasoningEffort == settings.effort else {
                return fail("AI channel request selection")
            }
            try model.reset()
            let storageKey = "\(RimesIdentity.preferenceKeyPrefix)PluginConfiguration.\(kind.pluginRawID)"
            defaults.set([
                AIChannelPluginConfigurationFieldID.model: "default",
                AIChannelPluginConfigurationFieldID.effort: "default",
            ], forKey: storageKey)
            try AITextGenerationPreferenceStore(defaults: defaults).setModelID(
                "legacy-model", for: kind
            )
            let restoredDefault = try AITextGenerationPreferenceStore(defaults: defaults)
                .channelSelection(
                    connectorKind: kind, configurationDefaults: defaults
                )
            guard restoredDefault.modelID == defaultModel,
                  restoredDefault.reasoningEffort == "medium",
                  defaults.dictionary(forKey: storageKey)?[AIChannelPluginConfigurationFieldID.model] as? String == defaultModel,
                  defaults.dictionary(forKey: storageKey)?[AIChannelPluginConfigurationFieldID.effort] as? String == "medium" else {
                return fail("AI channel legacy default migration")
            }
        }
        guard try PluginConfigurationCatalog.makeModel(
            pluginID: AITextProviderKind.openAICompatible.pluginRawID
        ) == nil else {
            return fail("AI API configuration owner")
        }

        let streamModel = try PluginConfigurationCatalog.makeStreamInputModel(
            defaults: defaults,
            notificationCenter: center
        )
        var streamSnapshot = try streamModel.load()
        guard streamSnapshot.string(
                StreamInputPluginConfigurationFieldID.connector
              ) == AITextProviderKind.openAICompatible.rawValue,
              streamSnapshot.number(
                StreamInputPluginConfigurationFieldID.candidateCount
              ) == 5,
              streamSnapshot.string(
                StreamInputPluginConfigurationFieldID.responsePace
              ) == StreamInputResponsePace.balanced.rawValue else {
            return fail("stream defaults")
        }

        // A v1.1 profile has no candidate/pace fields. Loading projects its
        // custom numeric timing onto the nearest v1.2 preset while retaining
        // the independently selected connector.
        let streamStorageKey =
            "\(RimesIdentity.preferenceKeyPrefix)PluginConfiguration.\(BuiltInPluginID.streamInput)"
        defaults.set([
            StreamInputPluginConfigurationFieldID.connector:
                AITextProviderKind.claudeCodeCLI.rawValue,
            StreamInputPluginConfigurationFieldID.debounceSeconds: 0.35,
            StreamInputPluginConfigurationFieldID.maximumWaitSeconds: 1.20,
        ], forKey: streamStorageKey)
        streamSnapshot = try streamModel.load()
        guard streamSnapshot.string(
                StreamInputPluginConfigurationFieldID.connector
              ) == AITextProviderKind.claudeCodeCLI.rawValue,
              streamSnapshot.number(
                StreamInputPluginConfigurationFieldID.candidateCount
              ) == 5,
              streamSnapshot.string(
                StreamInputPluginConfigurationFieldID.responsePace
              ) == StreamInputResponsePace.stable.rawValue else {
            return fail("stream v1.1 migration")
        }
        streamSnapshot[StreamInputPluginConfigurationFieldID.connector] =
            .string(AITextProviderKind.codexCLI.rawValue)
        streamSnapshot[
            StreamInputPluginConfigurationFieldID.candidateCount
        ] = .number(4)
        streamSnapshot[
            StreamInputPluginConfigurationFieldID.responsePace
        ] = .string(StreamInputResponsePace.fast.rawValue)
        _ = try streamModel.save(streamSnapshot)
        let streamSettings = PluginConfigurationCatalog.streamInputSettings(
            defaults: defaults
        )
        guard streamSettings.connectorKind == .codexCLI,
              streamSettings.candidateCount == 4,
              streamSettings.responsePace == .fast,
              streamSettings.debounce == 0.14,
              streamSettings.maximumWait == 0.50,
              let persistedStream = defaults.dictionary(
                forKey: streamStorageKey
              ),
              persistedStream[
                StreamInputPluginConfigurationFieldID.debounceSeconds
              ] == nil,
              persistedStream[
                StreamInputPluginConfigurationFieldID.maximumWaitSeconds
              ] == nil else {
            return fail("stream runtime bridge")
        }
        var fractionalCandidateSnapshot = streamSnapshot
        fractionalCandidateSnapshot[
            StreamInputPluginConfigurationFieldID.candidateCount
        ] = .number(2.5)
        do {
            _ = try streamModel.save(fractionalCandidateSnapshot)
            return fail("fractional stream candidate count accepted")
        } catch PluginConfigurationError.invalidField {
            // Expected.
        }

        // Translation is Apple-only: its schema carries languages and nothing
        // that could route source text to an AI channel.
        let translationModel = try PluginConfigurationCatalog
            .makeRealtimeTranslationModel(
                defaults: defaults,
                notificationCenter: center
            )
        guard translationModel.schema.fields.map(\.id) == [
                RealtimeTranslationPluginConfigurationFieldID.sourceLanguage,
                RealtimeTranslationPluginConfigurationFieldID.targetLanguage,
              ] else {
            return fail("translation schema is local-only")
        }
        var translationSnapshot = try translationModel.load()
        guard translationSnapshot.string(
                RealtimeTranslationPluginConfigurationFieldID.sourceLanguage
              ) == AppleTranslationWorkspace.defaultSourceLanguageID,
              translationSnapshot.string(
                RealtimeTranslationPluginConfigurationFieldID.targetLanguage
              ) == AppleTranslationWorkspace.defaultTargetLanguageID else {
            return fail("translation defaults")
        }
        translationSnapshot[
            RealtimeTranslationPluginConfigurationFieldID.sourceLanguage
        ] = .string("ja")
        translationSnapshot[
            RealtimeTranslationPluginConfigurationFieldID.targetLanguage
        ] = .string("en")
        _ = try translationModel.save(translationSnapshot)
        let translationSettings = PluginConfigurationCatalog
            .realtimeTranslationSettings(defaults: defaults)
        guard selectionStore.selectedKind == .codexCLI,
              translationSettings.sourceLanguageID == "ja",
              translationSettings.targetLanguageID == "en",
              defaults.dictionary(
                forKey:
                    "\(RimesIdentity.preferenceKeyPrefix)PluginConfiguration.\(BuiltInPluginID.appleTranslation)"
              ) != nil else {
            return fail("translation runtime bridge")
        }

        let privateSchema = PluginConfigurationSchema(
            pluginID: "smoke.private",
            title: "Private smoke",
            fields: [
                .secureText(
                    id: "credential",
                    title: "Credential",
                    maximumLength: 256
                ),
            ]
        )
        let privateStore = try PluginConfigurationPrivateJSONStore(
            storageIdentifier: privateSchema.pluginID,
            rootDirectory: privateRoot
        )
        let privateModel = try PluginConfigurationModel(
            schema: privateSchema,
            store: privateStore,
            notificationCenter: center
        )
        guard pluginConfigurationLayoutIsSafe(
            models: [streamModel, translationModel, privateModel]
        ) else {
            return fail("sheet layout")
        }
        let privateBase = privateRoot.appendingPathComponent(
            "plugin-config",
            isDirectory: true
        )
        let privatePluginDirectory = privateBase.appendingPathComponent(
            privateSchema.pluginID,
            isDirectory: true
        )
        let privateCanary = [
            "credential",
            UUID().uuidString,
            UUID().uuidString,
        ].joined(separator: "-")
        try FileManager.default.createDirectory(
            at: privateBase,
            withIntermediateDirectories: false,
            attributes: [.posixPermissions: 0o700]
        )
        try FileManager.default.createDirectory(
            at: privatePluginDirectory,
            withIntermediateDirectories: false,
            attributes: [.posixPermissions: 0o700]
        )
        let orphanedPrivateURL = privatePluginDirectory
            .appendingPathComponent(
                ".configuration.\(UUID().uuidString).tmp",
                isDirectory: false
            )
        guard FileManager.default.createFile(
            atPath: orphanedPrivateURL.path,
            contents: Data(privateCanary.utf8),
            attributes: [.posixPermissions: 0o600]
        ),
              try privateModel.load().string("credential") == "",
              !FileManager.default.fileExists(
                atPath: orphanedPrivateURL.path
              ) else {
            return fail("orphan private cleanup")
        }
        var privateSnapshot = try privateModel.load()
        privateSnapshot["credential"] = .string(privateCanary)
        let savedPrivateSnapshot = try privateModel.save(privateSnapshot)
        guard try privateModel.load().string("credential") == privateCanary,
              String(describing: savedPrivateSnapshot)
                .contains(privateCanary) == false,
              String(reflecting: savedPrivateSnapshot)
                .contains(privateCanary) == false,
              String(reflecting: savedPrivateSnapshot["credential"])
                .contains(privateCanary) == false else {
            return fail("private value redaction")
        }

        func permissions(at url: URL) throws -> Int {
            let attributes = try FileManager.default.attributesOfItem(
                atPath: url.path
            )
            guard let value = attributes[.posixPermissions] as? NSNumber else {
                throw PluginConfigurationError.unreadable
            }
            return value.intValue & 0o777
        }
        guard try permissions(at: privateRoot) == 0o755,
              try permissions(at: privateBase) == 0o700,
              try permissions(at: privatePluginDirectory) == 0o700,
              try permissions(at: privateStore.configurationURL) == 0o600 else {
            return fail("private permissions")
        }

        try FileManager.default.setAttributes(
            [.posixPermissions: 0o644],
            ofItemAtPath: privateStore.configurationURL.path
        )
        do {
            _ = try privateModel.load()
            return fail("weak private permissions accepted")
        } catch PluginConfigurationError.invalidPermissions {
            // Expected.
        }
        try FileManager.default.setAttributes(
            [.posixPermissions: 0o600],
            ofItemAtPath: privateStore.configurationURL.path
        )

        try FileManager.default.setAttributes(
            [.posixPermissions: 0o777],
            ofItemAtPath: privateRoot.path
        )
        do {
            _ = try privateModel.load()
            return fail("writable shared root accepted")
        } catch PluginConfigurationError.invalidPermissions {
            // Expected.
        }
        try FileManager.default.setAttributes(
            [.posixPermissions: 0o755],
            ofItemAtPath: privateRoot.path
        )

        try FileManager.default.removeItem(at: privateStore.configurationURL)
        try FileManager.default.createSymbolicLink(
            at: privateStore.configurationURL,
            withDestinationURL: URL(fileURLWithPath: "/dev/null")
        )
        do {
            _ = try privateModel.load()
            return fail("private symlink accepted")
        } catch PluginConfigurationError.unsafePath {
            // Expected.
        }
    } catch {
        return fail("model operation")
    }

    let notificationResult = notificationProbe.result
    guard notificationResult.count > 0,
          notificationResult.allRedacted else {
        return fail("redacted notification boundary")
    }

    print("PASS: plugin configuration smoke")
    return true
}

/// Reproduces the production sheet sequence, including AppKit's size
/// recalculation when assigning `contentViewController`. Controls must remain
/// inside a useful inset after the caller reapplies the controller's preferred
/// size.
private func pluginConfigurationLayoutIsSafe(
    models: [PluginConfigurationModel]
) -> Bool {
    guard Thread.isMainThread else { return false }
    _ = NSApplication.shared
    let tolerance: CGFloat = 0.5

    func approximatelyEqual(_ lhs: NSSize, _ rhs: NSSize) -> Bool {
        abs(lhs.width - rhs.width) <= tolerance
            && abs(lhs.height - rhs.height) <= tolerance
    }

    func descendants(of view: NSView) -> [NSView] {
        view.subviews.flatMap { [$0] + descendants(of: $0) }
    }

    for model in models {
        func rejectLayout(_ detail: String) -> Bool {
            print(
                "FAILED: plugin configuration layout "
                    + "\(model.schema.pluginID) \(detail)"
            )
            return false
        }
        let controller = PluginConfigurationViewController(model: model)
        _ = controller.view
        let expected = controller.preferredContentSize
        guard expected.width
                == PluginConfigurationViewController.preferredFormSize.width,
              expected.height
                >= PluginConfigurationViewController.minimumFormHeight,
              expected.height
                <= PluginConfigurationViewController.maximumFormHeight,
              approximatelyEqual(controller.view.frame.size, expected) else {
            return rejectLayout(
                "initial preferred=\(controller.preferredContentSize) "
                    + "view=\(controller.view.frame.size)"
            )
        }

        let sheet = PluginConfigurationSheetFactory.make(
            contentViewController: controller,
            title: "Layout smoke"
        )
        sheet.contentView?.layoutSubtreeIfNeeded()
        controller.view.layoutSubtreeIfNeeded()
        defer { sheet.close() }

        guard approximatelyEqual(sheet.contentLayoutRect.size, expected),
              approximatelyEqual(controller.view.bounds.size, expected) else {
            return rejectLayout(
                "panel=\(sheet.contentLayoutRect.size) "
                    + "view=\(controller.view.bounds.size)"
            )
        }

        let buttonViews = descendants(of: controller.view).compactMap {
            $0 as? NSButton
        }
        guard buttonViews.allSatisfy({ button in
            button is RimePointingHandButton
                || button is RimeFixedAccentPopUpButton
        }) else {
            let missingOwners = buttonViews.filter { button in
                !(button is RimePointingHandButton)
                    && !(button is RimeFixedAccentPopUpButton)
            }.map { String(describing: type(of: $0)) }
            return rejectLayout(
                "pointing-hand-owner=\(missingOwners.joined(separator: ","))"
            )
        }

        let interactiveViews = descendants(of: controller.view).filter {
            if let textField = $0 as? NSTextField {
                return textField.isEditable
            }
            return $0 is NSButton
                || $0 is NSStepper
                || $0 is NSSwitch
                || $0 is RimeFixedAccentSwitch
        }
        guard interactiveViews.count >= model.schema.fields.count + 3 else {
            return rejectLayout(
                "interactive-count=\(interactiveViews.count)"
            )
        }
        for interactiveView in interactiveViews
        where !interactiveView.isHidden {
            let frame = interactiveView.convert(
                interactiveView.bounds,
                to: controller.view
            )
            guard frame.width > 0,
                  frame.height > 0,
                  // Rounded AppKit buttons extend their focus-ring frame
                  // roughly seven points beyond the stack's 28-point inset.
                  frame.minX >= 16 - tolerance,
                  frame.maxX <= expected.width - 16 + tolerance,
                  frame.minY >= -tolerance,
                  frame.maxY <= expected.height + tolerance else {
                return rejectLayout(
                    "control=\(type(of: interactiveView)) frame=\(frame)"
                )
            }
        }
    }
    return true
}

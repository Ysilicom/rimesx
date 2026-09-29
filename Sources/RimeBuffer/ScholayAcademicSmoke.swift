import AppKit
import Foundation

private final class AcademicSmokeProvider: AITextProvider {
    let kind: AITextProviderKind = .codexCLI
    let availability: AITextProviderAvailability = .ready
    private(set) var request: AITextProviderRequest?
    private var completion:
        ((Result<[AITextProviderBlock], AITextProviderError>) -> Void)?

    func generate(
        _ request: AITextProviderRequest,
        onEvent: @escaping (AITextProviderEvent) -> Void,
        completion: @escaping (Result<[AITextProviderBlock], AITextProviderError>) -> Void
    ) -> any AITextCancellable {
        self.request = request
        self.completion = completion
        return AITextNoopCancellation()
    }

    func finish(_ text: String) {
        completion?(.success([
            AITextProviderBlock(index: 0, text: text, title: nil),
        ]))
    }
}

func runScholayAcademicSmokeTest() -> Bool {
    func fail(_ reason: String) -> Bool {
        print("FAILED: academic plugins \(reason)")
        return false
    }
    let entries = BuiltInPlugins.makeAll().map(\.descriptor)
    for (id, name) in [
        (BuiltInPluginID.codexCLI, "OpenAI"),
        (BuiltInPluginID.claudeCodeCLI, "Anthropic"),
        (BuiltInPluginID.scholay, "Scholay"),
        (BuiltInPluginID.polisher, "Scholay"),
        (BuiltInPluginID.latex, "Scholay"),
    ] {
        guard entries.first(where: { $0.key.rawID == id })?.producerName
            == name else { return fail("producer \(id)") }
    }

    let png = Data(base64Encoded:
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC"
    )!
    let pasteboard = NSPasteboard(name: NSPasteboard.Name(
        "ScholayAcademicSmoke.\(UUID())"
    ))
    pasteboard.declareTypes([.png], owner: nil)
    guard pasteboard.setData(png, forType: .png),
          BufferImagePasteboard.read(pasteboard) != nil else {
        return fail("PNG clipboard decode")
    }
    let model = BufferModel()
    let attachment = BufferModel.ImageAttachment(
        pngData: png, pixelWidth: 1, pixelHeight: 1
    )
    guard model.insertPastedImage(attachment) != nil,
          model.stagedText.isEmpty,
          model.pendingDeliveryBlocks.isEmpty,
          !AITextSourcePolicy.accepts(model.blocks),
          AITextSourcePolicy.accepts(model.blocks, allowImages: true) else {
        return fail("image remains non-text and cannot be delivered")
    }

    let request: URLRequest
    do {
        request = try AITextOpenAIRequestBuilder.makeRequest(
            configuration: OpenAICompatibleConfiguration(
                baseURL: "https://example.org/v1", model: "vision",
                apiKey: "test-key"
            ),
            sourceText: "", preparedPrompt: "Transcribe formula",
            imageInputs: [AITextImageInput(pngData: png)]
        )
    } catch { return fail("vision request: \(error)") }
    guard let body = request.httpBody,
          let envelope = try? JSONSerialization.jsonObject(with: body)
            as? [String: Any],
          let messages = envelope["messages"] as? [[String: Any]],
          let content = messages.last?["content"] as? [[String: Any]],
          content.contains(where: { $0["type"] as? String == "image_url" })
    else { return fail("image transport envelope") }

    let suite = "ScholayAcademicSmoke.\(UUID())"
    guard let defaults = UserDefaults(suiteName: suite) else {
        return fail("defaults")
    }
    defer { defaults.removePersistentDomain(forName: suite) }
    let options = ScholayAcademicOptions(defaults: defaults)
    options.selectLatexMode(.png)
    let provider = AcademicSmokeProvider()
    let connectors = AITextConnectorRegistry(providers: [provider])
    let workspace = ScholayAcademicWorkspace(
        kind: .latex, sourceModel: model, connectors: connectors,
        options: options, isSelected: { true }, selectionResolver: { kind in
            AITextGenerationSelection(
                connectorKind: kind, modelID: nil, mode: .direct,
                destination: .inline, format: .plain
            )
        }
    )
    workspace.start()
    defer { workspace.stop() }
    var protectionNotifications = 0
    let protectionObserver = NotificationCenter.default.addObserver(
        forName: .derivedBufferWorkspaceDidChange,
        object: workspace, queue: nil
    ) { _ in protectionNotifications += 1 }
    defer { NotificationCenter.default.removeObserver(protectionObserver) }
    workspace.setProtected(false)
    guard protectionNotifications == 0 else {
        return fail("unchanged protection retriggered refresh")
    }
    workspace.setProtected(true)
    workspace.setProtected(true)
    guard protectionNotifications == 1, !workspace.canGenerate else {
        return fail("protection notification repeated")
    }
    workspace.setProtected(false)
    guard protectionNotifications == 2 else {
        return fail("protection release notification")
    }
    guard workspace.canGenerate, workspace.generate(),
          provider.request?.imageInputs == [AITextImageInput(pngData: png)]
    else { return fail("PNG generation request") }
    provider.finish("x^2 + y^2 = z^2")
    guard workspace.deliveryPendingBlocks.first?.text == "x^2 + y^2 = z^2"
    else { return fail("LaTeX result delivery") }
    guard model.removeLastCharacter(), model.blocks.isEmpty,
          workspace.deliveryPendingBlocks.isEmpty else {
        return fail("backspace removes image card and invalidates result")
    }
    print("scholay academic smoke: OK")
    return true
}

import Foundation

private final class ScholaySmokeURLProtocol: URLProtocol {
    static var requestWasValid = false
    static var requestDiagnostics = ""
    static var responseBody = Data()

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        var body = request.httpBody ?? Data()
        if body.isEmpty, let stream = request.httpBodyStream {
            stream.open()
            defer { stream.close() }
            var buffer = [UInt8](repeating: 0, count: 1_024)
            while stream.hasBytesAvailable {
                let count = stream.read(&buffer, maxLength: buffer.count)
                if count <= 0 { break }
                body.append(contentsOf: buffer.prefix(count))
            }
        }
        Self.requestWasValid = request.httpMethod == "POST"
            && request.url?.path == "/v1/stc/papers/search"
            && request.value(forHTTPHeaderField: "Authorization")
                == "Bearer sk-" + String(repeating: "0", count: 64)
            && String(data: body, encoding: .utf8)?
                .contains("sleep deprivation memory") == true
        Self.requestDiagnostics = "method=\(request.httpMethod ?? "none") path=\(request.url?.path ?? "none") auth=\(request.value(forHTTPHeaderField: "Authorization") != nil) body=\(!body.isEmpty)"
        let response = HTTPURLResponse(
            url: request.url!, statusCode: 200,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response,
                            cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.responseBody)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

private final class ScholaySmokeCancellation: AITextCancellable {
    private(set) var cancelled = false
    func cancel() { cancelled = true }
}

private final class ScholaySmokeProvider: AITextProvider {
    let kind: AITextProviderKind = .codexCLI
    let availability: AITextProviderAvailability = .ready
    private(set) var requests: [AITextProviderRequest] = []
    private var completions: [
        (Result<[AITextProviderBlock], AITextProviderError>) -> Void
    ] = []

    func generate(
        _ request: AITextProviderRequest,
        onEvent: @escaping (AITextProviderEvent) -> Void,
        completion: @escaping (Result<[AITextProviderBlock], AITextProviderError>) -> Void
    ) -> any AITextCancellable {
        requests.append(request)
        completions.append(completion)
        return ScholaySmokeCancellation()
    }

    func complete(index: Int, text: String) {
        completions[index](.success([
            AITextProviderBlock(index: 0, text: text, title: nil),
        ]))
    }
}

private final class ScholaySmokeSearch: ScholayPaperSearching {
    private(set) var query: String?
    private(set) var keyWasPassed = false
    private var completion: ((Result<[ScholayPaper], ScholayPluginError>) -> Void)?

    func search(
        query: String,
        apiKey: String,
        completion: @escaping (Result<[ScholayPaper], ScholayPluginError>) -> Void
    ) -> any AITextCancellable {
        self.query = query
        keyWasPassed = apiKey == "sk-" + String(repeating: "0", count: 64)
        self.completion = completion
        return ScholaySmokeCancellation()
    }

    func complete(_ papers: [ScholayPaper]) {
        completion?(.success(papers))
    }
}

func runScholayPluginSmokeTest() -> Bool {
    func fail(_ message: String) -> Bool {
        print("FAILED: Scholay \(message)")
        return false
    }

    let sampleJSON = """
    {"id":"paper-1","source":"stc","title":"Sleep and memory",
     "abstract":"Sleep loss impairs memory consolidation in adults.",
     "year":2022,"venue":"Journal of Sleep Research",
     "doi":"10.1000/sleep.2022","authors":[{"name":"Jane Smith"}]}
    """
    guard let paper = try? JSONDecoder().decode(
        ScholayPaper.self, from: Data(sampleJSON.utf8)
    ), paper.hasUsableEvidence else { return fail("paper decode") }
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [ScholaySmokeURLProtocol.self]
    ScholaySmokeURLProtocol.responseBody = Data(
        "{\"success\":true,\"data\":{\"items\":[\(sampleJSON)]}}".utf8
    )
    let client = ScholayMinicodClient(session: URLSession(configuration: configuration))
    let searchFinished = DispatchSemaphore(value: 0)
    var searchedPapers: [ScholayPaper]?
    let searchTask = client.search(
        query: "sleep deprivation memory",
        apiKey: "sk-" + String(repeating: "0", count: 64)
    ) { result in
        if case let .success(papers) = result { searchedPapers = papers }
        searchFinished.signal()
    }
    guard searchFinished.wait(timeout: .now() + 5) == .success,
          ScholaySmokeURLProtocol.requestWasValid,
          searchedPapers == [paper] else {
        searchTask.cancel()
        return fail("REST request and response envelope \(ScholaySmokeURLProtocol.requestDiagnostics) count=\(searchedPapers?.count ?? -1)")
    }
    let block = AITextProviderBlock(
        index: 0,
        text: "睡眠不足可能损害记忆巩固[[1]]。",
        title: nil
    )
    guard let numericSections = try? ScholayCitationFormatter.renderSections(
        blocks: [block], papers: [paper], style: .gbT7714
    ), numericSections.body.contains("[1]"),
          numericSections.references.contains("https://doi.org/"),
          numericSections.combined.components(separatedBy: "\n").count == 2,
          let authorYear = try? ScholayCitationFormatter.renderSections(
            blocks: [block], papers: [paper], style: .apa7
          ), authorYear.body.contains("(Smith, 2022)"),
          let mla = try? ScholayCitationFormatter.renderSections(
            blocks: [block], papers: [paper], style: .mla9
          ), mla.body.contains("(Smith)"),
          mla.references.contains("Smith, Jane"),
          let chicago = try? ScholayCitationFormatter.renderSections(
            blocks: [block], papers: [paper], style: .chicagoAuthorDate
          ), chicago.body.contains("(Smith 2022)"),
          let vancouver = try? ScholayCitationFormatter.renderSections(
            blocks: [block], papers: [paper], style: .vancouver
          ), vancouver.body.contains("(1)"),
          let ieee = try? ScholayCitationFormatter.renderSections(
            blocks: [block], papers: [paper], style: .ieee
          ), ieee.body.contains("[1]"),
          ieee.references.contains("J. Smith") else {
        return fail("six citation styles and two sections")
    }
    guard (try? ScholayCitationFormatter.render(
        blocks: [AITextProviderBlock(index: 0,
                                     text: "无根据[[2]]。", title: nil)],
        papers: [paper], style: .gbT7714
    )) == nil else { return fail("fabricated citation accepted") }

    let twoAuthorJSON = sampleJSON.replacingOccurrences(
        of: "[{\"name\":\"Jane Smith\"}]",
        with: "[{\"name\":\"Jane Smith\"},{\"name\":\"Alex Jones\"}]"
    )
    guard let twoAuthorPaper = try? JSONDecoder().decode(
        ScholayPaper.self, from: Data(twoAuthorJSON.utf8)
    ), let apaPair = try? ScholayCitationFormatter.renderSections(
        blocks: [block], papers: [twoAuthorPaper], style: .apa7
    ), apaPair.body.contains("(Smith & Jones, 2022)"),
       apaPair.references.contains("Smith, J., & Jones, A."),
       let mlaPair = try? ScholayCitationFormatter.renderSections(
        blocks: [block], papers: [twoAuthorPaper], style: .mla9
       ), mlaPair.body.contains("(Smith and Jones)"),
       mlaPair.references.contains("Smith, Jane, and Alex Jones") else {
        return fail("two-author style formatting")
    }

    let root = FileManager.default.temporaryDirectory
        .appendingPathComponent("rimes-scholay-smoke-\(UUID().uuidString)")
    defer { try? FileManager.default.removeItem(at: root) }
    do {
        let model = try ScholayConfiguration.makeModel(rootDirectory: root)
        var snapshot = try model.load()
        snapshot[ScholayConfiguration.apiKeyFieldID] = .string(
            "sk-" + String(repeating: "0", count: 64)
        )
        try model.save(snapshot)
        guard try model.load().string(ScholayConfiguration.apiKeyFieldID)
                == snapshot.string(ScholayConfiguration.apiKeyFieldID),
              let attributes = try? FileManager.default.attributesOfItem(
                atPath: root.appendingPathComponent(
                    "plugin-config/builtin.scholay/configuration.json"
                ).path
              ),
              attributes[.posixPermissions] as? Int == 0o600 else {
            return fail("private key configuration")
        }
    } catch {
        return fail("private key configuration setup")
    }

    let suite = "rimes-scholay-smoke-\(UUID().uuidString)"
    guard let defaults = UserDefaults(suiteName: suite) else {
        return fail("preferences suite")
    }
    defer { defaults.removePersistentDomain(forName: suite) }
    let center = NotificationCenter()
    let preferences = ScholayToolbarPreferences(
        defaults: defaults, notificationCenter: center
    )
    let provider = ScholaySmokeProvider()
    let registry = AITextConnectorRegistry(providers: [provider])
    let searcher = ScholaySmokeSearch()
    let source = BufferModel()
    source.append("睡眠不足会降低记忆力")
    let workspace = ScholayWorkspace(
        sourceModel: source,
        connectorRegistry: registry,
        searcher: searcher,
        preferences: preferences,
        apiKeyLoader: { "sk-" + String(repeating: "0", count: 64) },
        selectionResolver: { kind in
            AITextGenerationSelection(
                connectorKind: kind,
                modelID: nil,
                mode: .direct,
                destination: .inline,
                format: .plain
            )
        },
        notificationCenter: center,
        isSelected: { true }
    )
    workspace.start()
    defer { workspace.stop() }
    guard workspace.canGenerate, workspace.generate(),
          provider.requests.count == 1 else {
        return fail("query generation")
    }
    provider.complete(index: 0, text: "sleep deprivation memory")
    guard searcher.query == "sleep deprivation memory",
          searcher.keyWasPassed else { return fail("Minicod search") }
    searcher.complete([paper])
    guard provider.requests.count == 2,
          provider.requests[1].preparedPrompt?.contains("EVIDENCE_JSON") == true else {
        return fail("evidence rewrite request")
    }
    provider.complete(index: 1, text: block.text)
    let rail = workspace.railSnapshot
    guard rail.outputRowsAreIndependent,
          rail.outputRows.count == 2,
          rail.outputRows.map(\.title) == ["正文", "文献"],
          let bodyID = rail.outputRows[0].blocks.first?.id,
          let referencesID = rail.outputRows[1].blocks.first?.id,
          workspace.outputSectionText(for: bodyID) == numericSections.body,
          workspace.outputSectionText(for: referencesID)
            == numericSections.references,
          workspace.copyableSectionText(
            for: bodyID, protected: false, source: workspace
          ) == numericSections.body,
          workspace.copyableSectionText(
            for: referencesID, protected: false, source: workspace
          ) == numericSections.references,
          workspace.copyableSectionText(
            for: bodyID, protected: true, source: workspace
          ) == nil,
          workspace.copyableSectionText(
            for: UUID(), protected: false, source: workspace
          ) == nil else {
        return fail("independent copyable output sections")
    }
    guard workspace.phase == .ready,
          workspace.deliveryPendingBlocks.count == 1,
          workspace.deliveryPendingBlocks[0].text == numericSections.combined else {
        return fail("atomic delivery of both sections")
    }
    let generation = workspace.deliveryGeneration
    let outputID = workspace.deliveryPendingBlocks[0].id
    guard workspace.consumeDeliveredAndReportTerminalDrain(
        blockIDs: [outputID], generation: generation
    ) != nil, source.blocks.isEmpty else {
        return fail("terminal delivery")
    }
    guard workspace.outputSectionText(for: bodyID) == nil,
          workspace.outputSectionText(for: referencesID) == nil,
          workspace.copyableSectionText(
            for: bodyID, protected: false, source: workspace
          ) == nil else {
        return fail("stale section copy was accepted")
    }
    print("PASS: Scholay search, six styles, two copyable boxes, private key, and atomic delivery")
    return true
}

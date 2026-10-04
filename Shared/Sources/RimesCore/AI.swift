import Foundation

public struct ProviderConfiguration: Codable, Identifiable, Equatable {
    public var id: UUID
    public var name: String
    public var baseURL: String
    public var model: String
    public init(id: UUID = UUID(), name: String = "", baseURL: String = "", model: String = "") { self.id = id; self.name = name; self.baseURL = baseURL; self.model = model }
    public func endpoint(_ resource: String) throws -> URL {
        guard let u = URL(string: baseURL), u.scheme == "https", let host = u.host, !host.isEmpty,
              u.user == nil, u.password == nil, u.query == nil, u.fragment == nil else { throw CoreError.invalidEndpoint }
        return u.appendingPathComponent(resource)
    }
    public var consentIdentity: String { (try? endpoint("chat/completions").absoluteString) ?? "" }
}
/// Each AI plugin is its own Buffer plugin with its own instruction; none runs until
/// the user explicitly taps Run.
public enum AIPrompt {
    public static let polish = "Polish the supplied text without changing its meaning or language. Return only the polished text."
    /// Quick Q&A: a short, everyday answer, like a friend replying in chat.
    public static let ask = """
    像朋友聊天一样，用口语在 2 到 4 句话里回答：先给结论，再补一句关键原因或例子。
    不用术语、标题、列表或 Markdown，不客套。
    不要提自己是 AI 或模型，也不说"无法联网""无法获取最新信息"之类的话；问到排名、价格、新闻等会变的内容时，直接按已知情况回答，最后轻提一句"可能已有变化"。
    用提问的语言回答。
    """
}
/// How much the model should think. Services spell this differently, so each level lists the
/// request fields to try in order; callers step to the next when a service rejects one (HTTP
/// 400/422) and remember what worked. The last entry is always "send nothing".
public enum AIThinking: String, CaseIterable, Codable {
    case off, minimal, low, medium, high, auto
    public var attempts: [[String: Any]] { attempts(model: "") }
    /// DeepSeek (V4 included) turns thinking off only with `thinking: {type: disabled}`, and its
    /// `reasoning_effort` knows only low/high/max, with thinking on at high by default; routers
    /// such as CometAPI pass `thinking` through. So DeepSeek models get DeepSeek's own spelling first.
    public func attempts(model: String) -> [[String: Any]] {
        let disabled: [String: Any] = ["thinking": ["type": "disabled"]]
        if model.lowercased().contains("deepseek") {
            let enabled = ["type": "enabled"]
            switch self {
            case .off: return [disabled, ["reasoning_effort": "none"], [:]]
            case .minimal, .low: return [["thinking": enabled, "reasoning_effort": "low"], ["reasoning_effort": "low"], [:]]
            case .medium, .high: return [["thinking": enabled, "reasoning_effort": "high"], ["reasoning_effort": "high"], [:]]
            case .auto: return [[:]]
            }
        }
        switch self {
        case .off:
            // DeepSeek/GLM-style switch, Qwen-style switch, OpenAI-style effort, then nothing.
            return [disabled, ["enable_thinking": false], ["reasoning_effort": "none"], ["reasoning_effort": "minimal"], [:]]
        case .minimal: return [["reasoning_effort": "minimal"], ["reasoning_effort": "low"], [:]]
        case .low: return [["reasoning_effort": "low"], [:]]
        case .medium: return [["reasoning_effort": "medium"], [:]]
        case .high: return [["reasoning_effort": "high"], [:]]
        case .auto: return [[:]]
        }
    }
}
public enum AIRequest {
    /// `extra` adds request fields, such as one of `AIThinking.attempts`.
    public static func make(provider: ProviderConfiguration, key: String, source: String, instruction: String, consent: String,
                            extra: [String: Any] = [:]) throws -> URLRequest {
        guard !provider.model.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw CoreError.invalidEndpoint }
        let url = try provider.endpoint("chat/completions")
        guard consent == provider.consentIdentity else { throw CoreError.noConsent }
        guard source.utf8.count <= 64 * 1024 else { throw CoreError.tooLarge }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 45)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        var body: [String: Any] = ["model": provider.model, "stream": true, "messages": [["role": "system", "content": instruction], ["role": "user", "content": source]]]
        for (field, value) in extra where body[field] == nil { body[field] = value }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        return request
    }
}
public struct SSETextDecoder {
    private var pending = Data()
    private var eventData = [String]()
    private var eventBytes = 0
    /// Answer text, with any leading `<think>` block removed.
    public private(set) var text = ""
    /// Thinking so far (up to 16 KB), from `reasoning_content`/`reasoning` deltas or a `<think>` block; display only.
    public private(set) var reasoning = ""
    private var content = ""
    public private(set) var finished = false
    private var successfulFinish = false
    public init() {}
    public mutating func append(_ data: Data) throws {
        guard pending.count + data.count <= 1024 * 1024, text.utf8.count <= 256 * 1024 else { throw CoreError.tooLarge }
        pending.append(data)
        while let i = pending.firstIndex(of: 10) {
            let bytes = pending[..<i]; pending.removeSubrange(...i)
            guard let s = String(data: bytes, encoding: .utf8) else { throw CoreError.incomplete }
            let line = s.hasSuffix("\r") ? String(s.dropLast()) : s
            if line.isEmpty { try event(); continue }
            if line.hasPrefix("data:") {
                let value = String(line.dropFirst(5)).trimmingCharacters(in: .whitespaces)
                eventBytes += value.utf8.count + 1
                guard eventBytes <= 1024 * 1024 else { throw CoreError.tooLarge }
                eventData.append(value)
            }
        }
    }
    private mutating func event() throws {
        guard !eventData.isEmpty else { return }
        let payload = eventData.joined(separator: "\n"); eventData.removeAll(); eventBytes = 0
        if payload == "[DONE]" { guard successfulFinish else { throw CoreError.incomplete }; finished = true; return }
        guard !finished, let data = payload.data(using: .utf8),
              let root = try JSONSerialization.jsonObject(with: data) as? [String: Any], root["error"] == nil,
              let choices = root["choices"] as? [[String: Any]] else { throw CoreError.incomplete }
        guard let first = choices.first else { return } // optional usage-only event
        if let reason = first["finish_reason"] as? String {
            guard reason == "stop" else { throw CoreError.incomplete }; successfulFinish = true
        }
        if let delta = first["delta"] as? [String: Any] {
            for key in ["reasoning_content", "reasoning"] { if let value = delta[key] as? String { noteReasoning(value) } }
            if let value = delta["content"] as? String { content += value; splitThinking() }
        }
        guard content.utf8.count <= 256 * 1024 else { throw CoreError.tooLarge }
    }
    private mutating func noteReasoning(_ value: String) {
        guard reasoning.utf8.count < 16 * 1024 else { return } // enough to show; never trimmed, so it only grows
        reasoning += value
    }
    /// Some models stream their thinking inline as `<think>…</think>` before the answer.
    private mutating func splitThinking() {
        let lead = content.drop { $0.isWhitespace }
        guard lead.hasPrefix("<think>") else { text = content; return }
        let inner = lead.dropFirst("<think>".count)
        if let close = inner.range(of: "</think>") {
            reasoning = String(String(inner[..<close.lowerBound]).prefix(8000))
            text = String(inner[close.upperBound...].drop { $0.isWhitespace })
        } else { reasoning = String(String(inner).prefix(8000)); text = "" }
    }
    public mutating func complete() throws -> String {
        if !pending.isEmpty { try append(Data("\n\n".utf8)) }
        try event()
        guard finished, successfulFinish, !text.isEmpty else { throw CoreError.incomplete }; return text
    }
}
/// Reject every redirect: credentials never follow redirects, including cross-origin ones.
public final class NoRedirectSessionDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    public func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
public final class AIClient: @unchecked Sendable {
    private let makeConfiguration: @Sendable () -> URLSessionConfiguration
    public init(configuration: @escaping @Sendable () -> URLSessionConfiguration = { .ephemeral }) { makeConfiguration = configuration }
    /// `progress` receives the answer so far and the recent thinking.
    public func generate(_ request: URLRequest, progress: @escaping @Sendable (_ text: String, _ reasoning: String) async -> Void) async throws -> String {
        let config = makeConfiguration()
        config.urlCache = nil; config.httpCookieStorage = nil; config.httpShouldSetCookies = false
        config.timeoutIntervalForResource = 90
        let session = URLSession(configuration: config, delegate: NoRedirectSessionDelegate(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw CoreError.response((response as? HTTPURLResponse)?.statusCode ?? 0) }
        var decoder = SSETextDecoder(), packet = Data(), previous = "", previousReasoning = ""
        for try await byte in bytes {
            try Task.checkCancellation(); packet.append(byte)
            if byte == 10 || packet.count >= 4096 {
                try decoder.append(packet); packet.removeAll(keepingCapacity: true)
                if decoder.text != previous || decoder.reasoning != previousReasoning {
                    previous = decoder.text; previousReasoning = decoder.reasoning; await progress(previous, previousReasoning)
                }
                if decoder.finished { break }
            }
        }
        if !packet.isEmpty { try decoder.append(packet) }
        return try decoder.complete()
    }
    public func models(provider: ProviderConfiguration, key: String) async throws -> [String] {
        let config = makeConfiguration(); config.urlCache = nil; config.httpCookieStorage = nil; config.httpShouldSetCookies = false
        config.timeoutIntervalForResource = 30
        let session = URLSession(configuration: config, delegate: NoRedirectSessionDelegate(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        var req = URLRequest(url: try provider.endpoint("models"), timeoutInterval: 15)
        req.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        let (bytes, response) = try await session.bytes(for: req)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw CoreError.response((response as? HTTPURLResponse)?.statusCode ?? 0) }
        var data = Data()
        for try await byte in bytes {
            try Task.checkCancellation()
            guard data.count < 1024 * 1024 else { throw CoreError.tooLarge }
            data.append(byte)
        }
        let object = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        return (object?["data"] as? [[String: Any]] ?? []).compactMap { $0["id"] as? String }.sorted()
    }
}

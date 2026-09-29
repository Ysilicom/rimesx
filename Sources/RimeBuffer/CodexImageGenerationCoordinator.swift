import Darwin
import Foundation

extension Notification.Name {
    static let codexImageGenerationDidChange = Notification.Name(
        "RimeBuffer.CodexImageGeneration.didChange"
    )
}

/// A process-owned image request. It deliberately has no Buffer lifecycle:
/// closing or pausing the composer cannot terminate an accepted generation.
final class CodexImageGenerationCoordinator {
    static let shared = CodexImageGenerationCoordinator()

    private let store: MailboxStore

    init(store: MailboxStore = .shared) {
        self.store = store
    }

    enum Phase: Equatable {
        case idle
        case connecting
        case generating
        case saving
        case ready(UUID)
        case failed(String)
    }

    private final class Job {
        let handle: MailboxGenerationHandle
        let relay = AITextCancellationRelay()
        var imagePath: String?
        var toolStarted = false

        init(handle: MailboxGenerationHandle) { self.handle = handle }
    }

    private(set) var phase: Phase = .idle {
        didSet {
            guard oldValue != phase else { return }
            NotificationCenter.default.post(name: .codexImageGenerationDidChange,
                                            object: self)
        }
    }
    private var job: Job?
    private var startedAt: TimeInterval?
    private var progressTimer: Timer?

    var isRunning: Bool { job != nil }
    var elapsedSeconds: Int {
        guard let startedAt else { return 0 }
        return Int(max(0, ProcessInfo.processInfo.systemUptime - startedAt))
    }

    @discardableResult
    func start(prompt: String, modelID: String?,
               reasoningEffort: String?) throws -> UUID {
        dispatchPrecondition(condition: .onQueue(.main))
        guard job == nil else { throw MailboxStoreError.generationAlreadyRunning }
        guard let provider = AITextConnectorRegistry.shared.provider(for: .codexCLI),
              provider.availability == .ready else {
            throw AITextProviderError.unavailable("ChatGPT 连接器不可用")
        }
        let content = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !content.isEmpty,
              content.utf8.count <= AITextRuntimeLimits.maximumSourceBytes else {
            throw AITextProviderError.resultTooLarge
        }
        let handle = try store.beginAIConversation(
            source: .codexCLI(model: modelID),
            title: "图片 · \(String(content.prefix(60)))",
            prompt: content
        )
        let current = Job(handle: handle)
        job = current
        startedAt = ProcessInfo.processInfo.systemUptime
        progressTimer?.invalidate()
        let timer = Timer(timeInterval: 1, repeats: true) { [weak self] _ in
            guard let self, self.job != nil else { return }
            NotificationCenter.default.post(name: .codexImageGenerationDidChange,
                                            object: self)
        }
        progressTimer = timer
        RunLoop.main.add(timer, forMode: .common)
        phase = .connecting
        let request = AITextProviderRequest(
            requestID: UUID(),
            sourceText: content,
            preparedPrompt: """
            请使用已挂载的 imagegen Skill，调用图片生成工具，按下面的用户描述创作一张图片。
            图片由应用单独保存和展示；请勿把本地路径、Markdown 图片或 base64 放入回复。

            用户描述：\(content)
            """,
            modelID: modelID,
            reasoningEffort: reasoningEffort,
            skill: .imagegen
        )
        let task = provider.generate(request, onEvent: { [weak self, weak current] event in
            Self.onMain {
                guard let self, let current, self.job === current else { return }
                switch event {
                case .imageGenerationStarted:
                    current.toolStarted = true
                    self.phase = .generating
                case let .imageArtifactPath(path):
                    current.imagePath = path
                    self.phase = .saving
                case .activity, .reasoningSnapshot, .blockSnapshot:
                    break
                }
            }
        }, completion: { [weak self, weak current] result in
            Self.onMain {
                guard let self, let current, self.job === current else { return }
                self.finish(result, job: current)
            }
        })
        current.relay.install(task)
        return handle.threadID
    }

    private func finish(_ result: Result<[AITextProviderBlock], AITextProviderError>,
                        job current: Job) {
        do {
            switch result {
            case let .failure(error): throw error
            case .success:
                guard current.toolStarted,
                      let path = current.imagePath else {
                    throw AITextProviderError.invalidResult
                }
                let data = try Self.readGeneratedImage(at: path)
                try store.completeImageGeneration(
                    current.handle,
                    response: "图片已生成。",
                    pngData: data,
                    author: "ChatGPT"
                )
                stopProgressTimer()
                job = nil
                phase = .ready(current.handle.threadID)
                return
            }
        } catch {
            let message = (error as? AITextProviderError)?.userFacingMessage
                ?? error.localizedDescription
            try? store.failGeneration(current.handle, message: message)
            stopProgressTimer()
            job = nil
            phase = .failed(message)
        }
    }

    private func stopProgressTimer() {
        progressTimer?.invalidate()
        progressTimer = nil
        startedAt = nil
    }

    private static func readGeneratedImage(at path: String) throws -> Data {
        let root = AITextCodexHomeStore.shared.homeDirectory
            .appendingPathComponent("generated_images", isDirectory: true)
            .resolvingSymlinksInPath().standardizedFileURL.path
        let resolved = URL(fileURLWithPath: path)
            .resolvingSymlinksInPath().standardizedFileURL.path
        guard resolved.hasPrefix(root + "/"),
              resolved.hasSuffix(".png") else {
            throw AITextProviderError.invalidResult
        }
        let fd = open(resolved, O_RDONLY | O_NOFOLLOW)
        guard fd >= 0 else { throw AITextProviderError.invalidResult }
        defer { close(fd) }
        var info = stat()
        guard fstat(fd, &info) == 0,
              (info.st_mode & S_IFMT) == S_IFREG,
              info.st_uid == geteuid(),
              info.st_size >= 24,
              info.st_size <= 24 * 1_048_576 else {
            throw AITextProviderError.invalidResult
        }
        var data = Data()
        data.reserveCapacity(Int(info.st_size))
        var chunk = [UInt8](repeating: 0, count: 65_536)
        while true {
            let count = Darwin.read(fd, &chunk, chunk.count)
            if count == 0 { break }
            guard count > 0, data.count + count <= 24 * 1_048_576 else {
                throw AITextProviderError.invalidResult
            }
            data.append(contentsOf: chunk.prefix(count))
        }
        return data
    }

    private static func onMain(_ body: @escaping () -> Void) {
        if Thread.isMainThread { body() }
        else { DispatchQueue.main.async(execute: body) }
    }
}

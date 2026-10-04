import Foundation

/// The Capsule rail's tabs. Recent is the local clipboard history; every other
/// tab is one kind of saved Capsule entry with explicit card actions.
enum CapsuleRailTab: Hashable {
    case recent
    case captures
    case saved(CapsuleEntryKind)

    /// Most-used first. Password requires in-place authentication; copying
    /// is a separate explicit action available only during its reveal lease.
    static var ordered: [CapsuleRailTab] {
        if !CapsuleNavigationPolicy.usesModules {
            return [
                .recent, .captures, .saved(.note), .saved(.image),
                .saved(.video), .saved(.pdf), .saved(.skill), .saved(.password),
            ]
        }
        return CapsuleModuleAvailability.enabled.map { module -> CapsuleRailTab in
            switch module {
            case .temporary: return .recent
            case .capture: return .captures
            case .notes: return .saved(.note)
            case .resources: return .saved(.resource)
            case .passwords: return .saved(.password)
            }
        }
    }

    var label: String {
        if !CapsuleNavigationPolicy.usesModules {
            switch self {
            case .saved(.image): return "图库"
            case .saved(.video): return "影集"
            case .saved(.skill): return "技能"
            default: break
            }
        }
        switch self {
        case .recent: return "临时"
        case .captures: return "捕获"
        case .saved(.resource): return "资源"
        case let .saved(kind): return kind.tabLabel
        }
    }

    var savedKind: CapsuleEntryKind? {
        if case let .saved(kind) = self { return kind }
        return nil
    }

    /// The tab `offset` steps away, wrapping at both ends.
    func cycled(by offset: Int) -> CapsuleRailTab {
        let tabs = Self.ordered
        guard let index = tabs.firstIndex(of: self) else { return tabs.first ?? .recent }
        guard !tabs.isEmpty else { return .recent }
        let count = tabs.count
        return tabs[((index + offset) % count + count) % count]
    }
}

/// One saved entry as the rail shows it.
struct CapsuleRailEntry: Equatable, Identifiable {
    let id: UUID
    let kind: CapsuleEntryKind
    let title: String
    let preview: String
    let updatedAt: Date
    /// Note text, or the absolute path of an Image, PDF or Skill. Always nil
    /// for a password: its secret stays encrypted on disk.
    let payload: String?
    /// Title plus body for ordinary entries; title and summary for a password.
    let searchText: String
    /// Note header properties shown as the first fields in the detail view.
    var headerFields: [CapsuleField] = []
    /// Set for a note whose file could not be read as a Capsule document.
    var formatIssue: String? = nil
    /// Structured bibliographic metadata for PDF-backed literature items.
    var reference: CapsuleReference? = nil
    /// The module filter this entry belongs to (the same classification the
    /// Capsule manager uses), for the rail's left filter column.
    var classification: CapsuleModuleFilter = .other
}

enum CapsuleRailActivationRules {
    enum Action: Equatable {
        /// Plain text: straight into the target box through the focus token.
        case insertText
        /// A file: onto the pasteboard, then pasted into the target app.
        case pasteFile
        /// Authenticate and reveal in the card; activation never copies/delivers.
        case revealInPlace
    }

    static func action(for kind: CapsuleEntryKind) -> Action {
        switch kind {
        case .note: return .insertText
        case .image, .pdf, .skill, .video, .resource: return .pasteFile
        case .password: return .revealInPlace
        }
    }

    static func allowsCopy(_ kind: CapsuleEntryKind) -> Bool {
        action(for: kind) != .revealInPlace
    }
}

enum CapsuleRailCountText {
    /// The header count, singular for exactly one.
    static func items(_ count: Int) -> String {
        count == 1 ? "1 ITEM" : "\(count) ITEMS"
    }
}

enum CapsuleRailSearchRules {
    static func filter(_ entries: [CapsuleRailEntry],
                       query: String) -> [CapsuleRailEntry] {
        let terms = query.split(whereSeparator: \Character.isWhitespace)
            .map(String.init)
        guard !terms.isEmpty else { return entries }
        return entries.filter { entry in
            terms.allSatisfy {
                entry.searchText.localizedCaseInsensitiveContains($0)
            }
        }
    }
}

/// The saved entries behind the rail's Capsule tabs. Reading every Markdown
/// file can take a while on a large library, so loads run on a background
/// queue and the rail renders whatever was last published.
final class CapsuleRailLibrary {
    enum LoadState: Equatable {
        case idle
        case loading
        case loaded
        case failed
    }

    typealias Loader = () -> (content: Result<[CapsuleRailEntry], Error>,
                               passwords: Result<[CapsuleRailEntry], Error>)

    var onChange: (() -> Void)?
    var usesLiveCaptureHistory = false
    let savedIndex: CapsuleRailSavedIndex

    private let loader: Loader
    private let passwordReader: ((CapsuleRailEntry) throws -> String)?
    private let queue = DispatchQueue(
        label: "RIMES.CapsuleRail.library",
        qos: .userInitiated
    )
    private var generation: UInt64 = 0
    private var byKind: [CapsuleEntryKind: [CapsuleRailEntry]] = [:]
    private var stateByKind: [CapsuleEntryKind: LoadState] = [:]
    private var notePayloads: Set<String> = []
    private var contentEntryIDs: Set<UUID> = []

    init(loader: @escaping Loader,
         savedIndex: CapsuleRailSavedIndex = CapsuleRailSavedIndex(defaults: nil),
         passwordReader: ((CapsuleRailEntry) throws -> String)? = nil) {
        self.loader = loader
        self.savedIndex = savedIndex
        self.passwordReader = passwordReader
    }

    /// A library that never reads anything, for panes built without stores.
    static func inert() -> CapsuleRailLibrary {
        CapsuleRailLibrary(loader: { (.success([]), .success([])) })
    }

    /// Reads the real Capsule stores.
    static func live() -> CapsuleRailLibrary {
        let library = CapsuleRailLibrary(
            loader: { load(contentStore: .shared, passwordStore: .shared) },
            savedIndex: CapsuleRailSavedIndex(defaults: .standard),
            passwordReader: { entry in
                let repository = CapsuleWindowRepository()
                guard let row = try repository.list(kind: .password).first(where: {
                    $0.id == entry.id && $0.updatedAt == entry.updatedAt && $0.title == entry.title
                }) else { throw CapsuleWindowRepositoryError.staleRecord }
                return try repository.draft(for: row).content
            }
        )
        library.usesLiveCaptureHistory = true
        return library
    }

    /// Only called after a successful current-credential challenge. This is
    /// deliberately separate from the metadata loader and search projection.
    func readPassword(_ entry: CapsuleRailEntry) throws -> String {
        guard entry.kind == .password, let passwordReader else {
            throw CapsuleWindowRepositoryError.staleRecord
        }
        return try passwordReader(entry)
    }

    func entries(for kind: CapsuleEntryKind) -> [CapsuleRailEntry] {
        dispatchPrecondition(condition: .onQueue(.main))
        if kind == .resource {
            return ([.pdf, .skill, .resource] as [CapsuleEntryKind])
                .flatMap { byKind[$0] ?? [] }
                .sorted { $0.updatedAt > $1.updatedAt }
        }
        return byKind[kind] ?? []
    }

    func state(for kind: CapsuleEntryKind) -> LoadState {
        dispatchPrecondition(condition: .onQueue(.main))
        return stateByKind[kind] ?? .idle
    }

    /// Whether a Recent card is already in Capsule: saved from the rail into
    /// an entry that still exists, or text that matches a note exactly.
    func isInCapsule(historyItemID: UUID, text: String?) -> Bool {
        dispatchPrecondition(condition: .onQueue(.main))
        if let entry = savedIndex.entryID(forHistoryItem: historyItemID),
           contentEntryIDs.contains(entry) {
            return true
        }
        return text.map(notePayloads.contains) ?? false
    }

    func recordSaved(historyItemID: UUID, entryID: UUID) {
        dispatchPrecondition(condition: .onQueue(.main))
        savedIndex.record(historyItem: historyItemID, entry: entryID)
    }

    /// Starts a fresh read. What is already shown stays until it lands.
    func reload() {
        dispatchPrecondition(condition: .onQueue(.main))
        generation &+= 1
        let generation = generation
        for kind in CapsuleEntryKind.allCases where stateByKind[kind] != .loaded {
            stateByKind[kind] = .loading
        }
        let loader = loader
        queue.async { [weak self] in
            let result = loader()
            DispatchQueue.main.async {
                self?.apply(result, generation: generation)
            }
        }
    }

    private func apply(
        _ result: (content: Result<[CapsuleRailEntry], Error>,
                   passwords: Result<[CapsuleRailEntry], Error>),
        generation: UInt64
    ) {
        dispatchPrecondition(condition: .onQueue(.main))
        guard generation == self.generation else { return }
        let contentKinds = CapsuleEntryKind.allCases.filter { $0 != .password }
        switch result.content {
        case let .success(entries):
            for kind in contentKinds {
                byKind[kind] = entries.filter { $0.kind == kind }
                stateByKind[kind] = .loaded
            }
            notePayloads = Set(entries.filter { $0.kind == .note }.compactMap(\.payload))
            contentEntryIDs = Set(entries.map(\.id))
            savedIndex.prune(keeping: contentEntryIDs)
        case let .failure(error):
            IMELog.write("capsule rail content load failed: \(error.localizedDescription)")
            for kind in contentKinds {
                byKind[kind] = []
                stateByKind[kind] = .failed
            }
        }
        switch result.passwords {
        case let .success(entries):
            byKind[.password] = entries
            stateByKind[.password] = .loaded
        case let .failure(error):
            IMELog.write("capsule rail password load failed: \(error.localizedDescription)")
            byKind[.password] = []
            stateByKind[.password] = .failed
        }
        onChange?()
    }

    /// Projects both stores into rail entries, newest first. Password entries
    /// carry only their title and a fixed mask.
    static func load(contentStore: CapsuleContentStore,
                     passwordStore: CapsulePasswordStore)
        -> (content: Result<[CapsuleRailEntry], Error>,
            passwords: Result<[CapsuleRailEntry], Error>) {
        let content = Result {
            try contentStore.listRecords().map { record in
                CapsuleRailEntry(
                    id: record.summary.id,
                    kind: record.summary.type,
                    title: record.summary.title,
                    preview: record.cardSummary,
                    updatedAt: record.summary.updatedAt,
                    payload: record.content,
                    searchText: ([record.summary.title, record.summaryText ?? "", record.content]
                        + (record.reference.map { [
                            $0.authors, $0.year, $0.container, $0.publisher,
                            $0.doi, $0.isbn,
                        ] } ?? [])
                        + record.headerFields.map { "\($0.label) \($0.value)" })
                        .joined(separator: "\n"),
                    headerFields: record.headerFields,
                    formatIssue: record.formatIssue,
                    reference: record.reference,
                    classification: CapsuleModuleClassification.content(record)
                )
            }.sorted { $0.updatedAt > $1.updatedAt }
        }
        let passwords = Result {
            try passwordStore.listSummaries().map { summary in
                CapsuleRailEntry(
                    id: summary.id,
                    kind: .password,
                    title: summary.title,
                    preview: summary.summaryText ?? summary.maskedPassword,
                    updatedAt: summary.updatedAt,
                    payload: nil,
                    searchText: summary.title + "\n" + (summary.summaryText ?? ""),
                    classification: CapsuleModuleClassification.password(summary.category)
                )
            }.sorted { $0.updatedAt > $1.updatedAt }
        }
        return (content, passwords)
    }
}

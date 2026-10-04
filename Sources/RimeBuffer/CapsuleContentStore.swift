import Darwin
import CryptoKit
import Foundation

enum CapsuleEntryKind: String, Codable, CaseIterable, Hashable {
    case password
    case skill
    case note
    case image
    case pdf
    case video
    case resource

    /// Kinds this build no longer offers. Their files stay on disk untouched;
    /// listing skips them so one retired record cannot hide every current one.
    static let retiredRawValues: Set<String> = ["prompt", "memory", "url"]

    var displayName: String {
        switch self {
        case .password: return "Password"
        case .skill: return "Skill"
        case .note: return "Note"
        case .image: return "Image"
        case .pdf: return "文献"
        case .video: return "视频"
        case .resource: return "资源"
        }
    }

    /// The Capsule sidebar is intentionally narrow. Keep the type selector
    /// readable as the local library grows beyond the original four kinds.
    var tabLabel: String {
        switch self {
        case .password: return "密码"
        case .skill: return "Skills"
        case .note: return "笔记"
        case .image: return "图片"
        case .pdf: return "文献"
        case .video: return "视频"
        case .resource: return "资源"
        }
    }

    var storesMarkdownText: Bool {
        switch self {
        case .note:
            return true
        case .password, .skill, .image, .pdf, .video, .resource:
            return false
        }
    }

    var storesLocalPath: Bool {
        switch self {
        case .skill, .image, .pdf, .video, .resource:
            return true
        case .password, .note:
            return false
        }
    }
}

enum CapsuleNoteFormat: String, Codable, CaseIterable {
    case other, bullet, richText = "rich-text"
}

enum CapsuleResourceType: String, Codable, CaseIterable {
    case document, project, other
}

struct CapsuleContentWriteRequest: Codable, Equatable {
    let id: UUID?
    let type: CapsuleEntryKind
    let title: String
    let content: String
    /// Optional one-line description shown on the card under the title.
    let summaryText: String?
    let reference: CapsuleReference?
    let noteFormat: CapsuleNoteFormat?
    let resourceType: CapsuleResourceType?
    /// Versioned native RTFD payload. The Markdown body is its readable
    /// Obsidian projection and is never silently imported over this data.
    let richTextData: Data?

    init(id: UUID? = nil,
         type: CapsuleEntryKind,
         title: String,
         content: String,
         summaryText: String? = nil,
         reference: CapsuleReference? = nil,
         noteFormat: CapsuleNoteFormat? = nil,
         resourceType: CapsuleResourceType? = nil,
         richTextData: Data? = nil) {
        self.id = id
        self.type = type
        self.title = title
        self.content = content
        self.summaryText = summaryText
        self.reference = reference
        self.noteFormat = noteFormat
        self.resourceType = resourceType
        self.richTextData = richTextData
    }
}

struct CapsuleContentSummary: Equatable, Identifiable {
    let id: UUID
    let type: CapsuleEntryKind
    let title: String
    let updatedAt: Date
    let fileURL: URL
}

struct CapsuleContentRecord: Equatable {
    let summary: CapsuleContentSummary
    let content: String
    /// The written one-line summary, if any.
    var summaryText: String? = nil
    var reference: CapsuleReference? = nil
    var noteFormat: CapsuleNoteFormat = .other
    var resourceType: CapsuleResourceType = .other
    var richTextData: Data? = nil
    var projectionConflict = false
    /// Header properties (for example added in Obsidian) shown as fields.
    var headerFields: [CapsuleField] = []
    /// Set when the file could not be read as a Capsule document. The record
    /// stays listed, with the raw file as its body, until it is saved again.
    var formatIssue: String? = nil

    /// What the card shows under the title: the written summary, else the
    /// first readable line.
    var cardSummary: String {
        if let summaryText { return summaryText }
        if summary.type == .note,
           let fallback = CapsuleEntryGrammar.fallbackSummary(body: content) {
            return fallback
        }
        return snippet
    }

    var snippet: String {
        switch summary.type {
        case .image:
            return "Image · " + URL(fileURLWithPath: content).lastPathComponent
        case .video:
            return "Video · " + URL(fileURLWithPath: content).lastPathComponent
        case .pdf:
            if let reference, !reference.isEmpty {
                return [reference.authors, reference.year, reference.kind.title]
                    .filter { !$0.isEmpty }.joined(separator: " · ")
            }
            return "待补书目信息 · " + URL(fileURLWithPath: content).lastPathComponent
        case .resource:
            return "本机资源 · " + URL(fileURLWithPath: content).lastPathComponent
        case .password, .skill, .note:
            break
        }
        let flattened = content
            .split(whereSeparator: \Character.isWhitespace)
            .joined(separator: " ")
        guard flattened.count > 72 else { return flattened }
        return String(flattened.prefix(72)) + "…"
    }
}

/// One immutable, lock-consistent view of a local Markdown entry for the
/// optional cloud mirror. Keeping the original bytes preserves Obsidian edits
/// and `updated_at`; the revision is content-addressed and contains no secret.
struct CapsuleContentSyncDocument: Equatable {
    let record: CapsuleContentRecord
    let data: Data
    let revision: String
}

struct CapsuleContentImportResult: Equatable {
    let received: Int
    let uniqueInput: Int
    let inserted: Int
    let skippedExisting: Int
    let skippedInputDuplicates: Int
}

private struct CapsuleContentIdentity: Hashable {
    let type: CapsuleEntryKind
    let title: String
    let content: String

    init(_ request: CapsuleContentWriteRequest) {
        type = request.type
        title = request.title
        content = request.content
    }

    init(_ record: CapsuleContentRecord) {
        type = record.summary.type
        title = record.summary.title
        content = record.content
    }
}

enum CapsuleContentStoreError: LocalizedError, Equatable {
    case unsafeStorage(String)
    case invalidRequest(String)
    case malformedDocument(String)
    case recordNotFound
    /// A file written by a build that still offered this kind. Not corruption,
    /// so listing skips it rather than failing.
    case retiredRecord
    case revisionConflict
    case fileOperation(String)

    var errorDescription: String? {
        switch self {
        case let .unsafeStorage(path):
            return "Capsule 本地目录不安全：\(path)"
        case let .invalidRequest(message):
            return "Capsule 条目无效：\(message)"
        case let .malformedDocument(path):
            return "Capsule Markdown 格式无效：\(path)"
        case .recordNotFound:
            return "未找到 Capsule 条目"
        case .retiredRecord:
            return "该 Capsule 条目属于已下架的类型"
        case .revisionConflict:
            return "Capsule 条目已被其他窗口更新"
        case let .fileOperation(message):
            return "Capsule 本地文件操作失败：\(message)"
        }
    }
}

/// Obsidian-readable local store for non-secret Capsule units. Passwords keep
/// their authenticated encrypted format in `passwords/`; Prompt, Memory and
/// Skill/Image/PDF path units live here as ordinary Markdown so Obsidian can
/// read, edit and graph them without a product-specific database. The binary
/// asset remains at its user-managed local path; Capsule never copies it into
/// the executable or the repository.
final class CapsuleContentStore {
    static let shared = CapsuleContentStore()

    static let maximumDocumentBytes = 8 * 1_024 * 1_024
    static let maximumRecordCount = 20_000
    static let maximumTitleCharacters = 256
    static let maximumContentCharacters = 256 * 1_024
    static let defaultEntryID = UUID(
        uuidString: "72696d65-7300-4000-8000-000000000001"
    )!
    static let defaultEntryTitle = "RIMES 默认词条"
    static let defaultEntryContent = "RIMES"

    let rootURL: URL
    let entryDirectoryURL: URL
    let conflictDirectoryURL: URL
    let seedMarkerURL: URL

    private let fileManager: FileManager
    private let now: () -> Date

    init(rootURL: URL = CapsulePasswordStore.defaultRootURL(),
         fileManager: FileManager = .default,
         now: @escaping () -> Date = Date.init) {
        self.rootURL = rootURL.standardizedFileURL
        entryDirectoryURL = self.rootURL.appendingPathComponent(
            "entries",
            isDirectory: true
        )
        conflictDirectoryURL = self.rootURL.appendingPathComponent(
            "conflicts",
            isDirectory: true
        )
        seedMarkerURL = self.rootURL.appendingPathComponent("content-seed-v1")
        self.fileManager = fileManager
        self.now = now
    }

    /// Seeds once per local Capsule library. The marker deliberately survives
    /// deletion of the preset so an intentional user removal remains final.
    @discardableResult
    func seedDefaultsIfNeeded() throws -> Bool {
        try withStoreLock {
            try prepareDirectoriesWithoutLock()
            if fileManager.fileExists(atPath: seedMarkerURL.path) {
                try requireSafeRegularFile(seedMarkerURL, maximumBytes: 64)
                return false
            }
            let existing = try recordsWithoutLock()
            var inserted = false
            if !existing.contains(where: {
                $0.summary.id == Self.defaultEntryID
                    || $0.summary.title == Self.defaultEntryTitle
            }) {
                _ = try writeRecordWithoutLock(
                    CapsuleContentWriteRequest(
                        id: Self.defaultEntryID,
                        type: .note,
                        title: Self.defaultEntryTitle,
                        content: Self.defaultEntryContent
                    ),
                    requiresExistingID: false
                )
                inserted = true
            }
            try writePrivateFileWithoutLock(
                Data("seeded\n".utf8),
                to: seedMarkerURL
            )
            return inserted
        }
    }

    func listRecords() throws -> [CapsuleContentRecord] {
        try withStoreLock {
            try prepareDirectoriesWithoutLock()
            try seedDefaultsWithoutLockIfNeeded()
            return try recordsWithoutLock()
        }
    }

    func search(_ query: String,
                kind: CapsuleEntryKind? = nil,
                limit: Int = 5) throws
        -> [CapsuleContentRecord] {
        let terms = query
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .split(whereSeparator: \Character.isWhitespace)
            .map(String.init)
        guard !terms.isEmpty else { return [] }
        return try listRecords().lazy.filter { record in
            guard kind == nil || record.summary.type == kind else {
                return false
            }
            let reference = record.reference.map {
                [$0.authors, $0.year, $0.container, $0.publisher,
                 $0.doi, $0.isbn].joined(separator: "\n")
            } ?? ""
            let searchable = (record.summary.title + "\n" + record.content + "\n" + reference)
                .lowercased()
            return terms.allSatisfy(searchable.contains)
        }
        .prefix(min(max(limit, 1), 20))
        .map { $0 }
    }

    @discardableResult
    func put(_ request: CapsuleContentWriteRequest,
             expectedRevision: String? = nil) throws
        -> CapsuleContentSummary {
        let normalized = try Self.validate(request)
        try requireAvailableAssetIfNeeded(normalized)
        return try withStoreLock {
            try prepareDirectoriesWithoutLock()
            try seedDefaultsWithoutLockIfNeeded()
            return try writeRecordWithoutLock(
                normalized,
                requiresExistingID: normalized.id != nil,
                expectedRevision: expectedRevision
            )
        }
    }

    /// Keeps read/deduplicate/write under one process-wide flock so two CLI
    /// importers cannot both insert the same ordinary Capsule entry.
    func importUnique(_ requests: [CapsuleContentWriteRequest]) throws
        -> CapsuleContentImportResult {
        guard requests.count <= Self.maximumRecordCount,
              requests.allSatisfy({ $0.id == nil }) else {
            throw CapsuleContentStoreError.invalidRequest(
                "导入数量超过上限或包含记录 ID"
            )
        }
        let normalized = try requests.map(Self.validate)
        for request in normalized {
            try requireAvailableAssetIfNeeded(request)
        }
        return try withStoreLock {
            try prepareDirectoriesWithoutLock()
            try seedDefaultsWithoutLockIfNeeded()
            let records = try recordsWithoutLock()
            var existing = Set(records.map(CapsuleContentIdentity.init))
            var uniqueInput = Set<CapsuleContentIdentity>()
            var inserted = 0
            var skippedExisting = 0
            var skippedInputDuplicates = 0
            var recordCount = records.count
            for request in normalized {
                let identity = CapsuleContentIdentity(request)
                guard uniqueInput.insert(identity).inserted else {
                    skippedInputDuplicates += 1
                    continue
                }
                guard !existing.contains(identity) else {
                    skippedExisting += 1
                    continue
                }
                guard recordCount < Self.maximumRecordCount else {
                    throw CapsuleContentStoreError.invalidRequest(
                        "记录数量超过上限"
                    )
                }
                _ = try writeRecordWithoutLock(
                    request,
                    requiresExistingID: false,
                    knownRecordCount: recordCount
                )
                existing.insert(identity)
                inserted += 1
                recordCount += 1
            }
            return CapsuleContentImportResult(
                received: normalized.count,
                uniqueInput: uniqueInput.count,
                inserted: inserted,
                skippedExisting: skippedExisting,
                skippedInputDuplicates: skippedInputDuplicates
            )
        }
    }

    func record(id: UUID) throws -> CapsuleContentRecord {
        try withStoreLock {
            try prepareDirectoriesWithoutLock()
            try seedDefaultsWithoutLockIfNeeded()
            let url = entryURL(id: id)
            guard fileManager.fileExists(atPath: url.path) else {
                throw CapsuleContentStoreError.recordNotFound
            }
            let record: CapsuleContentRecord
            do {
                record = try parseDocumentWithoutLock(url)
            } catch CapsuleContentStoreError.malformedDocument {
                return try degradedRecordWithoutLock(url)
            }
            guard record.summary.id == id else {
                return try degradedRecordWithoutLock(url)
            }
            return record
        }
    }

    /// Reads all ordinary entries and their exact Markdown bytes while holding
    /// the same cross-process lock used by CRUD. Password documents are owned
    /// by `CapsulePasswordStore` and can never enter this snapshot.
    func synchronizationDocuments() throws -> [CapsuleContentSyncDocument] {
        try withStoreLock {
            try prepareDirectoriesWithoutLock()
            try seedDefaultsWithoutLockIfNeeded()
            return try recordsWithoutLock().compactMap { record in
                // Skill bodies are device-local absolute paths. Uploading them
                // would disclose the Mac's directory layout while producing a
                // record that cannot be resolved safely on another device.
                guard record.summary.type != .skill,
                      record.summary.type != .resource,
                      record.summary.type != .video,
                      record.formatIssue == nil else { return nil }
                let url = record.summary.fileURL
                try requireSafeRegularFile(
                    url,
                    maximumBytes: Self.maximumDocumentBytes
                )
                let data: Data
                do {
                    data = try Data(contentsOf: url, options: [.mappedIfSafe])
                } catch {
                    throw CapsuleContentStoreError.fileOperation(
                        error.localizedDescription
                    )
                }
                return CapsuleContentSyncDocument(
                    record: record,
                    data: data,
                    revision: Self.revision(of: data)
                )
            }
        }
    }

    /// Before a cloud tombstone or winner consumes an unavailable local media
    /// edit, retain its exact Markdown under the same private store lock. This
    /// local copy may contain an absolute path and therefore never enters the
    /// iCloud mirror or sync state.
    @discardableResult
    func archiveSynchronizedConflict(
        _ data: Data,
        id: UUID,
        origin: String
    ) throws -> URL {
        guard !data.isEmpty,
              data.count <= Self.maximumDocumentBytes else {
            throw CapsuleContentStoreError.invalidRequest(
                "冲突 Markdown 为空或超过大小上限"
            )
        }
        return try withStoreLock {
            try prepareDirectoriesWithoutLock()
            let validationURL = entryURL(id: id)
            let record = try parseDocumentDataWithoutLock(
                data,
                fileURL: validationURL
            )
            guard record.summary.id == id,
                  record.summary.type != .password else {
                throw CapsuleContentStoreError.malformedDocument(
                    validationURL.path
                )
            }
            let itemDirectory = conflictDirectoryURL.appendingPathComponent(
                id.uuidString.lowercased(),
                isDirectory: true
            )
            try preparePrivateDirectory(itemDirectory)
            let existing = try fileManager.contentsOfDirectory(
                at: itemDirectory,
                includingPropertiesForKeys: nil,
                options: [.skipsHiddenFiles]
            )
            guard existing.count < 10_000 else {
                throw CapsuleContentStoreError.unsafeStorage(
                    itemDirectory.path
                )
            }
            let safeOrigin = origin.filter {
                $0.isLetter || $0.isNumber || $0 == "-"
            }
            let milliseconds = Int(now().timeIntervalSince1970 * 1_000)
            let destination = itemDirectory.appendingPathComponent(
                "\(milliseconds)-\(safeOrigin)-"
                    + "\(UUID().uuidString.lowercased()).md"
            )
            try writePrivateFileWithoutLock(
                data,
                to: destination,
                additionalAllowedDirectory: itemDirectory
            )
            return destination
        }
    }

    /// Applies a fully validated remote Markdown document under the local
    /// store lock. Media sync materializes its asset first, so the canonical
    /// local body remains an absolute path as required by the editor.
    @discardableResult
    func applySynchronizedDocument(
        _ data: Data,
        id: UUID,
        expectedRevision: String?
    ) throws
        -> CapsuleContentRecord {
        guard !data.isEmpty, data.count <= Self.maximumDocumentBytes else {
            throw CapsuleContentStoreError.invalidRequest(
                "同步 Markdown 为空或超过大小上限"
            )
        }
        return try withStoreLock {
            try prepareDirectoriesWithoutLock()
            try seedDefaultsWithoutLockIfNeeded()
            let destination = entryURL(id: id)
            let destinationExists = fileManager.fileExists(
                atPath: destination.path
            )
            if destinationExists {
                guard let expectedRevision,
                      try fileRevisionWithoutLock(destination)
                        == expectedRevision else {
                    throw CapsuleContentStoreError.revisionConflict
                }
            } else if expectedRevision != nil {
                throw CapsuleContentStoreError.revisionConflict
            } else if try recordsWithoutLock().count
                        >= Self.maximumRecordCount {
                throw CapsuleContentStoreError.invalidRequest(
                    "记录数量超过上限"
                )
            }
            let record = try parseDocumentDataWithoutLock(
                data,
                fileURL: destination
            )
            guard record.summary.id == id else {
                throw CapsuleContentStoreError.malformedDocument(
                    destination.path
                )
            }
            try requireAvailableAssetIfNeeded(
                CapsuleContentWriteRequest(
                    id: id,
                    type: record.summary.type,
                    title: record.summary.title,
                    content: record.content
                )
            )
            try writePrivateFileWithoutLock(data, to: destination)
            return record
        }
    }

    /// A tombstone may arrive after this device already removed the entry.
    /// Treat that as an idempotent success while retaining the store lock.
    func removeSynchronizedRecord(
        id: UUID,
        expectedRevision: String?
    ) throws {
        try withStoreLock {
            try prepareDirectoriesWithoutLock()
            let destination = entryURL(id: id)
            guard fileManager.fileExists(atPath: destination.path) else {
                guard expectedRevision == nil else {
                    throw CapsuleContentStoreError.revisionConflict
                }
                return
            }
            guard let expectedRevision,
                  try fileRevisionWithoutLock(destination)
                    == expectedRevision else {
                throw CapsuleContentStoreError.revisionConflict
            }
            try requireSafeRegularFile(
                destination,
                maximumBytes: Self.maximumDocumentBytes
            )
            do {
                try fileManager.removeItem(at: destination)
            } catch {
                throw CapsuleContentStoreError.fileOperation(
                    error.localizedDescription
                )
            }
        }
    }

    func remove(id: UUID, expectedRevision: String? = nil) throws {
        try withStoreLock {
            let url = entryURL(id: id)
            guard fileManager.fileExists(atPath: url.path) else {
                throw CapsuleContentStoreError.recordNotFound
            }
            try requireSafeRegularFile(
                url,
                maximumBytes: Self.maximumDocumentBytes
            )
            if let expectedRevision {
                guard try fileRevisionWithoutLock(url) == expectedRevision else {
                    throw CapsuleContentStoreError.revisionConflict
                }
            }
            do {
                try fileManager.removeItem(at: url)
            } catch {
                throw CapsuleContentStoreError.fileOperation(
                    error.localizedDescription
                )
            }
        }
    }

    private func fileRevisionWithoutLock(_ url: URL) throws -> String {
        try requireSafeRegularFile(
            url,
            maximumBytes: Self.maximumDocumentBytes
        )
        let data: Data
        do {
            data = try Data(contentsOf: url, options: [.mappedIfSafe])
        } catch {
            throw CapsuleContentStoreError.fileOperation(
                error.localizedDescription
            )
        }
        return Self.revision(of: data)
    }

    private func seedDefaultsWithoutLockIfNeeded() throws {
        guard !fileManager.fileExists(atPath: seedMarkerURL.path) else {
            try requireSafeRegularFile(seedMarkerURL, maximumBytes: 64)
            return
        }
        let existing = try recordsWithoutLock()
        if !existing.contains(where: {
            $0.summary.id == Self.defaultEntryID
                || $0.summary.title == Self.defaultEntryTitle
        }) {
            _ = try writeRecordWithoutLock(
                CapsuleContentWriteRequest(
                    id: Self.defaultEntryID,
                    type: .note,
                    title: Self.defaultEntryTitle,
                    content: Self.defaultEntryContent
                ),
                requiresExistingID: false
            )
        }
        try writePrivateFileWithoutLock(Data("seeded\n".utf8), to: seedMarkerURL)
    }

    private func writeRecordWithoutLock(
        _ request: CapsuleContentWriteRequest,
        requiresExistingID: Bool,
        expectedRevision: String? = nil,
        knownRecordCount: Int? = nil
    ) throws -> CapsuleContentSummary {
        let normalized = try Self.validate(request)
        let id = normalized.id ?? UUID()
        if requiresExistingID {
            let destination = entryURL(id: id)
            guard fileManager.fileExists(atPath: destination.path) else {
                throw CapsuleContentStoreError.recordNotFound
            }
            try requireSafeRegularFile(
                destination,
                maximumBytes: Self.maximumDocumentBytes
            )
        } else {
            let recordCount = try knownRecordCount ?? recordsWithoutLock().count
            if recordCount >= Self.maximumRecordCount {
                throw CapsuleContentStoreError.invalidRequest("记录数量超过上限")
            }
        }
        let updatedAt = now()
        let document = Self.markdownDocument(
            id: id,
            type: normalized.type,
            title: normalized.title,
            updatedAt: updatedAt,
            content: normalized.content,
            summaryText: normalized.summaryText,
            reference: normalized.reference,
            noteFormat: normalized.noteFormat,
            resourceType: normalized.resourceType,
            richTextData: normalized.richTextData,
            preservedHeaderLines: requiresExistingID
                ? preservedHeaderLinesWithoutLock(entryURL(id: id))
                : []
        )
        let data = Data(document.utf8)
        guard data.count <= Self.maximumDocumentBytes else {
            throw CapsuleContentStoreError.invalidRequest("Markdown 超过大小上限")
        }
        let destination = entryURL(id: id)
        if let expectedRevision {
            guard try fileRevisionWithoutLock(destination)
                    == expectedRevision else {
                throw CapsuleContentStoreError.revisionConflict
            }
        }
        try writePrivateFileWithoutLock(data, to: destination)
        return CapsuleContentSummary(
            id: id,
            type: normalized.type,
            title: normalized.title,
            updatedAt: updatedAt,
            fileURL: destination
        )
    }

    private func recordsWithoutLock() throws -> [CapsuleContentRecord] {
        guard fileManager.fileExists(atPath: entryDirectoryURL.path) else {
            return []
        }
        try requireSafeDirectory(entryDirectoryURL)
        let urls: [URL]
        do {
            urls = try fileManager.contentsOfDirectory(
                at: entryDirectoryURL,
                includingPropertiesForKeys: [
                    .isRegularFileKey,
                    .isSymbolicLinkKey,
                    .fileSizeKey,
                ],
                options: [.skipsHiddenFiles]
            )
        } catch {
            throw CapsuleContentStoreError.fileOperation(
                error.localizedDescription
            )
        }
        guard urls.count <= Self.maximumRecordCount else {
            throw CapsuleContentStoreError.unsafeStorage(entryDirectoryURL.path)
        }
        return try urls.compactMap { url -> CapsuleContentRecord? in
            guard url.pathExtension.lowercased() == "md" else { return nil }
            let record: CapsuleContentRecord
            do {
                record = try parseDocumentWithoutLock(url)
            } catch CapsuleContentStoreError.retiredRecord {
                return nil
            } catch CapsuleContentStoreError.malformedDocument {
                // One unreadable file must never hide the rest, nor itself.
                return try degradedRecordWithoutLock(url)
            }
            guard url.deletingPathExtension().lastPathComponent
                    == record.summary.id.uuidString.lowercased() else {
                return try degradedRecordWithoutLock(url)
            }
            return record
        }
        .sorted {
            let order = $0.summary.title.localizedStandardCompare(
                $1.summary.title
            )
            if order == .orderedSame {
                return $0.summary.id.uuidString < $1.summary.id.uuidString
            }
            return order == .orderedAscending
        }
    }

    /// A note named by a UUID that no longer parses: listed with its raw text
    /// as the body so it can be read and tidied, never silently dropped.
    private func degradedRecordWithoutLock(_ url: URL) throws -> CapsuleContentRecord {
        guard let id = UUID(uuidString: url.deletingPathExtension().lastPathComponent) else {
            throw CapsuleContentStoreError.malformedDocument(url.path)
        }
        try requireSafeRegularFile(url, maximumBytes: Self.maximumDocumentBytes)
        let data: Data
        do {
            data = try Data(contentsOf: url, options: [.mappedIfSafe])
        } catch {
            throw CapsuleContentStoreError.fileOperation(error.localizedDescription)
        }
        let text = String(decoding: data, as: UTF8.self).replacingOccurrences(of: "\0", with: "")
        let lines = text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        let titleLine = lines.first { $0.hasPrefix("title:") }
            .flatMap { CapsuleFrontMatter.decodeScalar(String($0.dropFirst(6)).trimmingCharacters(in: .whitespaces)) }
        let title = titleLine.flatMap { $0.isEmpty ? nil : String($0.prefix(Self.maximumTitleCharacters)) }
            ?? "未命名笔记"
        let modified = (try? url.resourceValues(forKeys: [.contentModificationDateKey]))?
            .contentModificationDate ?? Date(timeIntervalSince1970: 0)
        return CapsuleContentRecord(
            summary: CapsuleContentSummary(
                id: id, type: .note, title: title, updatedAt: modified, fileURL: url
            ),
            content: text.isEmpty ? " " : String(text.prefix(Self.maximumContentCharacters)),
            summaryText: "格式待整理：打开编辑并保存即可修复",
            formatIssue: "这条笔记的文件格式无法识别，下面是原文。编辑并保存会把它整理为标准格式。"
        )
    }

    private func parseDocumentWithoutLock(_ url: URL) throws
        -> CapsuleContentRecord {
        guard url.deletingLastPathComponent().standardizedFileURL
                == entryDirectoryURL.standardizedFileURL else {
            throw CapsuleContentStoreError.unsafeStorage(url.path)
        }
        try requireSafeRegularFile(
            url,
            maximumBytes: Self.maximumDocumentBytes
        )
        let data: Data
        do {
            data = try Data(contentsOf: url, options: [.mappedIfSafe])
        } catch {
            throw CapsuleContentStoreError.fileOperation(
                error.localizedDescription
            )
        }
        return try parseDocumentDataWithoutLock(data, fileURL: url)
    }

    private func parseDocumentDataWithoutLock(
        _ data: Data,
        fileURL url: URL
    ) throws -> CapsuleContentRecord {
        guard !data.isEmpty, data.count <= Self.maximumDocumentBytes,
              let text = String(data: data, encoding: .utf8),
              !text.contains("\0") else {
            throw CapsuleContentStoreError.malformedDocument(url.path)
        }
        let lines = text.split(
            separator: "\n",
            omittingEmptySubsequences: false
        ).map(String.init)
        guard lines.count >= 8,
              lines[0] == "---",
              let end = lines.dropFirst().firstIndex(of: "---"),
              let header = try? CapsuleFrontMatter.parse(lines[1..<end]) else {
            throw CapsuleContentStoreError.malformedDocument(url.path)
        }
        if let kindRaw = header.scalar("capsule"),
           CapsuleEntryKind.retiredRawValues.contains(kindRaw) {
            throw CapsuleContentStoreError.retiredRecord
        }
        guard header.scalar("version") == "1",
              let kindRaw = header.scalar("capsule"),
              let kind = CapsuleEntryKind(rawValue: kindRaw),
              kind != .password,
              let idRaw = header.scalar("id"),
              let id = UUID(uuidString: idRaw),
              let title = header.scalar("title"),
              !title.isEmpty,
              title.count <= Self.maximumTitleCharacters,
              let updatedString = header.scalar("updated_at"),
              let updatedAt = Self.parseDate(updatedString) else {
            throw CapsuleContentStoreError.malformedDocument(url.path)
        }
        var bodyStart = end + 1
        if lines.indices.contains(bodyStart), lines[bodyStart].isEmpty {
            bodyStart += 1
        }
        let content = bodyStart < lines.count
            ? lines[bodyStart...].joined(separator: "\n")
            : ""
        let noteFormat = kind == .note
            ? CapsuleNoteFormat(rawValue: header.scalar("capsule_note_format") ?? "") ?? .other
            : .other
        let richTextData: Data?
        let projectionConflict: Bool
        if noteFormat == .richText {
            guard header.scalar("capsule_rich_version") == "1",
                  let encoded = header.scalar("capsule_rich_asset"),
                  let decoded = Data(base64Encoded: encoded),
                  decoded.count <= Self.maximumDocumentBytes,
                  header.scalar("capsule_rich_hash") == Self.sha256(decoded) else {
                throw CapsuleContentStoreError.malformedDocument(url.path)
            }
            richTextData = decoded
            projectionConflict = header.scalar("capsule_projection_hash")
                != Self.sha256(Data(content.utf8))
        } else {
            richTextData = nil
            projectionConflict = false
        }
        let normalized = try Self.validate(
            CapsuleContentWriteRequest(
                id: id,
                type: kind,
                title: title,
                content: content,
                noteFormat: kind == .note ? noteFormat : nil,
                resourceType: kind == .resource
                    ? CapsuleResourceType(rawValue: header.scalar("capsule_resource_type") ?? "") ?? .other
                    : nil,
                richTextData: richTextData
            )
        )
        return CapsuleContentRecord(
            summary: CapsuleContentSummary(
                id: id,
                type: kind,
                title: normalized.title,
                updatedAt: updatedAt,
                fileURL: url
            ),
            content: normalized.content,
            summaryText: CapsuleSummaryRules.normalized(header.scalar("summary")),
            reference: kind == .pdf ? CapsuleReference(header: header) : nil,
            noteFormat: noteFormat,
            resourceType: kind == .resource
                ? CapsuleResourceType(rawValue: header.scalar("capsule_resource_type") ?? "") ?? .other
                : .other,
            richTextData: richTextData,
            projectionConflict: projectionConflict,
            headerFields: kind == .note ? header.userFields : []
        )
    }

    private static func revision(of data: Data) -> String {
        SHA256.hash(data: data).map {
            String(format: "%02x", $0)
        }.joined()
    }

    private func entryURL(id: UUID) -> URL {
        entryDirectoryURL.appendingPathComponent(
            "\(id.uuidString.lowercased()).md"
        )
    }

    private func prepareDirectoriesWithoutLock() throws {
        try preparePrivateDirectory(rootURL)
        try preparePrivateDirectory(entryDirectoryURL)
        try preparePrivateDirectory(conflictDirectoryURL)
    }

    private func preparePrivateDirectory(_ url: URL) throws {
        if fileManager.fileExists(atPath: url.path) {
            try requireSafeDirectory(url)
        } else {
            do {
                try fileManager.createDirectory(
                    at: url,
                    withIntermediateDirectories: true,
                    attributes: [.posixPermissions: 0o700]
                )
            } catch {
                throw CapsuleContentStoreError.fileOperation(
                    error.localizedDescription
                )
            }
        }
        guard chmod(url.path, 0o700) == 0 else {
            throw CapsuleContentStoreError.fileOperation("无法收紧目录权限")
        }
    }

    private func requireSafeDirectory(_ url: URL) throws {
        let values = try safeValues(for: url)
        guard values.isDirectory == true,
              values.isSymbolicLink != true else {
            throw CapsuleContentStoreError.unsafeStorage(url.path)
        }
    }

    private func requireSafeRegularFile(_ url: URL,
                                        maximumBytes: Int) throws {
        let values = try safeValues(for: url)
        guard values.isRegularFile == true,
              values.isSymbolicLink != true,
              let size = values.fileSize,
              size > 0,
              size <= maximumBytes else {
            throw CapsuleContentStoreError.unsafeStorage(url.path)
        }
    }

    private func safeValues(for url: URL) throws -> URLResourceValues {
        do {
            return try url.resourceValues(forKeys: [
                .isDirectoryKey,
                .isRegularFileKey,
                .isSymbolicLinkKey,
                .fileSizeKey,
            ])
        } catch {
            throw CapsuleContentStoreError.fileOperation(
                error.localizedDescription
            )
        }
    }

    /// New media records must point at a real ordinary file. Parsing an
    /// existing record deliberately does not repeat this availability gate so
    /// a moved/offline asset remains editable and can render a missing-file
    /// placeholder instead of making the entire Markdown store unreadable.
    private func requireAvailableAssetIfNeeded(
        _ request: CapsuleContentWriteRequest
    ) throws {
        guard request.type == .image || request.type == .pdf else { return }
        let url = URL(fileURLWithPath: request.content).standardizedFileURL
        let values: URLResourceValues
        do {
            values = try url.resourceValues(forKeys: [
                .isRegularFileKey,
                .isSymbolicLinkKey,
            ])
        } catch {
            throw CapsuleContentStoreError.invalidRequest(
                "\(request.type.displayName) 文件不可用"
            )
        }
        guard values.isRegularFile == true,
              values.isSymbolicLink != true else {
            throw CapsuleContentStoreError.invalidRequest(
                "\(request.type.displayName) 必须指向普通、非符号链接文件"
            )
        }
    }

    private func writePrivateFileWithoutLock(
        _ data: Data,
        to url: URL,
        additionalAllowedDirectory: URL? = nil
    ) throws {
        let parent = url.deletingLastPathComponent().standardizedFileURL
        let allowedParents = [rootURL, entryDirectoryURL]
            .map(\.standardizedFileURL)
            + [additionalAllowedDirectory?.standardizedFileURL].compactMap { $0 }
        guard allowedParents.contains(parent) else {
            throw CapsuleContentStoreError.unsafeStorage(url.path)
        }
        let directory = url.deletingLastPathComponent()
        try preparePrivateDirectory(directory)
        let staged = directory.appendingPathComponent(
            ".\(url.lastPathComponent).\(UUID().uuidString).tmp"
        )
        do {
            try data.write(to: staged, options: [.atomic])
            guard chmod(staged.path, 0o600) == 0 else {
                throw CapsuleContentStoreError.fileOperation("无法收紧文件权限")
            }
            if fileManager.fileExists(atPath: url.path) {
                _ = try fileManager.replaceItemAt(
                    url,
                    withItemAt: staged,
                    backupItemName: nil,
                    options: []
                )
            } else {
                try fileManager.moveItem(at: staged, to: url)
            }
            guard chmod(url.path, 0o600) == 0 else {
                throw CapsuleContentStoreError.fileOperation("无法收紧文件权限")
            }
        } catch let error as CapsuleContentStoreError {
            try? fileManager.removeItem(at: staged)
            throw error
        } catch {
            try? fileManager.removeItem(at: staged)
            throw CapsuleContentStoreError.fileOperation(
                error.localizedDescription
            )
        }
    }

    private func withStoreLock<T>(_ body: () throws -> T) throws -> T {
        try preparePrivateDirectory(rootURL)
        let lockURL = rootURL.appendingPathComponent(".lock")
        let descriptor = open(
            lockURL.path,
            O_CREAT | O_RDWR | O_CLOEXEC,
            mode_t(0o600)
        )
        guard descriptor >= 0 else {
            throw CapsuleContentStoreError.fileOperation("无法打开存储锁")
        }
        defer { close(descriptor) }
        _ = fchmod(descriptor, mode_t(0o600))
        guard flock(descriptor, LOCK_EX) == 0 else {
            throw CapsuleContentStoreError.fileOperation("无法取得存储锁")
        }
        defer { flock(descriptor, LOCK_UN) }
        return try body()
    }

    private static func validate(_ request: CapsuleContentWriteRequest) throws
        -> CapsuleContentWriteRequest {
        let title = request.title.trimmingCharacters(
            in: .whitespacesAndNewlines
        )
        guard request.type != .password else {
            throw CapsuleContentStoreError.invalidRequest("Password 必须使用加密存储")
        }
        guard !title.isEmpty,
              title.count <= maximumTitleCharacters,
              !title.contains("\0") else {
            throw CapsuleContentStoreError.invalidRequest("标题为空或过长")
        }
        guard !request.content.isEmpty,
              request.content.count <= maximumContentCharacters,
              !request.content.contains("\0") else {
            throw CapsuleContentStoreError.invalidRequest("内容为空或过长")
        }
        if request.type.storesLocalPath {
            guard NSString(string: request.content).isAbsolutePath,
                  !request.content.contains("\n"),
                  !request.content.contains("\r") else {
                throw CapsuleContentStoreError.invalidRequest(
                    "\(request.type.displayName) 必须是单行绝对路径"
                )
            }
        }
        if request.type == .resource {
            let url = URL(fileURLWithPath: request.content).standardizedFileURL
            let values = try? url.resourceValues(forKeys: [
                .isRegularFileKey, .isDirectoryKey, .isSymbolicLinkKey,
            ])
            guard values?.isSymbolicLink != true,
                  values?.isRegularFile == true || values?.isDirectory == true else {
                throw CapsuleContentStoreError.invalidRequest("资源路径必须指向本机文件或目录")
            }
        }
        if request.type == .image {
            let allowed = Set([
                "png", "jpg", "jpeg", "heic", "webp", "tif", "tiff",
                "gif", "bmp",
            ])
            let ext = URL(fileURLWithPath: request.content)
                .pathExtension.lowercased()
            guard allowed.contains(ext) else {
                throw CapsuleContentStoreError.invalidRequest("Image 文件类型不受支持")
            }
        }
        if request.type == .pdf,
           URL(fileURLWithPath: request.content).pathExtension.lowercased()
                != "pdf" {
            throw CapsuleContentStoreError.invalidRequest("PDF 条目必须指向 .pdf 文件")
        }
        if request.type == .note && request.noteFormat == .richText {
            guard let data = request.richTextData,
                  !data.isEmpty,
                  data.count <= maximumDocumentBytes / 2 else {
                throw CapsuleContentStoreError.invalidRequest(
                    "富文本内容资产缺失或超过大小上限"
                )
            }
        }
        return CapsuleContentWriteRequest(
            id: request.id,
            type: request.type,
            title: title,
            content: request.content,
            summaryText: request.type == .note
                ? CapsuleSummaryRules.normalized(request.summaryText)
                : nil,
            reference: request.type == .pdf ? request.reference : nil,
            noteFormat: request.type == .note ? request.noteFormat : nil,
            resourceType: request.type == .resource ? request.resourceType : nil,
            richTextData: request.type == .note && request.noteFormat == .richText
                ? request.richTextData : nil
        )
    }

    /// Header keys RIMES does not own (Obsidian tags, user properties), read
    /// from the file being replaced so a save never drops them.
    private func preservedHeaderLinesWithoutLock(_ url: URL) -> [String] {
        guard let data = try? Data(contentsOf: url, options: [.mappedIfSafe]),
              let text = String(data: data, encoding: .utf8) else { return [] }
        let lines = text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        guard lines.first == "---",
              let end = lines.dropFirst().firstIndex(of: "---"),
              let header = try? CapsuleFrontMatter.parse(lines[1..<end]) else { return [] }
        return header.preservedLines
    }

    private static func markdownDocument(id: UUID,
                                         type: CapsuleEntryKind,
                                         title: String,
                                         updatedAt: Date,
                                         content: String,
                                         summaryText: String? = nil,
                                         reference: CapsuleReference? = nil,
                                         noteFormat: CapsuleNoteFormat? = nil,
                                         resourceType: CapsuleResourceType? = nil,
                                         richTextData: Data? = nil,
                                         preservedHeaderLines: [String] = []) -> String {
        var header = [
            "---",
            "capsule: \(type.rawValue)",
            "version: 1",
            "id: \(encodeJSONScalar(id.uuidString.lowercased()))",
            "title: \(encodeJSONScalar(title))",
            "updated_at: \(encodeJSONScalar(iso8601.string(from: updatedAt)))",
        ]
        if let summaryText {
            header.append("summary: \(encodeJSONScalar(summaryText))")
        }
        if let reference {
            header += reference.headerValues.map { "\($0.0): \(encodeJSONScalar($0.1))" }
        }
        if let noteFormat {
            header.append("capsule_note_format: \(encodeJSONScalar(noteFormat.rawValue))")
        }
        if let resourceType {
            header.append("capsule_resource_type: \(encodeJSONScalar(resourceType.rawValue))")
        }
        if noteFormat == .richText, let richTextData {
            header.append("capsule_rich_version: 1")
            header.append("capsule_rich_hash: \(encodeJSONScalar(sha256(richTextData)))")
            header.append("capsule_projection_hash: \(encodeJSONScalar(sha256(Data(content.utf8))))")
            header.append("capsule_rich_asset: \(encodeJSONScalar(richTextData.base64EncodedString()))")
        }
        header += preservedHeaderLines.filter { line in
            !line.hasPrefix("capsule_note_format:")
                && !line.hasPrefix("capsule_resource_type:")
                && !line.hasPrefix("capsule_rich_version:")
                && !line.hasPrefix("capsule_rich_hash:")
                && !line.hasPrefix("capsule_projection_hash:")
                && !line.hasPrefix("capsule_rich_asset:")
                && (reference == nil || !CapsuleReference.headerKeys.contains {
                    line.hasPrefix("\($0):")
                })
        }
        header.append("---")
        return header.joined(separator: "\n") + "\n\n" + content
    }

    private static func encodeJSONScalar(_ value: String) -> String {
        let data = try? JSONEncoder().encode(value)
        return data.flatMap { String(data: $0, encoding: .utf8) } ?? "\"\""
    }

    private static func sha256(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private static func decodeJSONScalar(_ value: String) throws -> String {
        guard let data = value.data(using: .utf8),
              let decoded = try? JSONDecoder().decode(String.self, from: data) else {
            throw CapsuleContentStoreError.malformedDocument("front matter")
        }
        return decoded
    }

    private static let iso8601: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let plainISO8601: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    private static func parseDate(_ value: String) -> Date? {
        iso8601.date(from: value) ?? plainISO8601.date(from: value)
    }
}

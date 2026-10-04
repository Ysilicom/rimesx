import Foundation

/// Bibliographic data belongs to the item; the PDF is only its attachment.
/// Empty metadata is valid for existing PDF bookmarks until the user catalogs them.
struct CapsuleReference: Equatable, Codable {
    enum Kind: String, CaseIterable, Codable {
        case book, journalArticle, conferencePaper, report

        var title: String {
            switch self {
            case .book: return "图书"
            case .journalArticle: return "期刊论文"
            case .conferencePaper: return "会议论文"
            case .report: return "报告"
            }
        }
    }

    var kind: Kind = .book
    var authors = ""
    var year = ""
    var container = ""
    var publisher = ""
    var volume = ""
    var issue = ""
    var pages = ""
    var doi = ""
    var url = ""
    var isbn = ""

    static let headerKeys: [String] = [
        "reference_type", "reference_authors", "reference_year",
        "reference_container", "reference_publisher", "reference_volume",
        "reference_issue", "reference_pages", "reference_doi",
        "reference_url", "reference_isbn",
    ]

    init() {}

    init(header: CapsuleFrontMatter) {
        kind = Kind(rawValue: header.scalar("reference_type") ?? "") ?? .book
        authors = header.scalar("reference_authors") ?? ""
        year = header.scalar("reference_year") ?? ""
        container = header.scalar("reference_container") ?? ""
        publisher = header.scalar("reference_publisher") ?? ""
        volume = header.scalar("reference_volume") ?? ""
        issue = header.scalar("reference_issue") ?? ""
        pages = header.scalar("reference_pages") ?? ""
        doi = header.scalar("reference_doi") ?? ""
        url = header.scalar("reference_url") ?? ""
        isbn = header.scalar("reference_isbn") ?? ""
    }

    var isEmpty: Bool {
        [authors, year, container, publisher, volume, issue, pages, doi, url, isbn]
            .allSatisfy { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    var missingFields: [String] {
        var missing: [String] = []
        if authors.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { missing.append("作者") }
        if year.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { missing.append("年份") }
        switch kind {
        case .book, .report:
            if publisher.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { missing.append("出版社 / 机构") }
        case .journalArticle, .conferencePaper:
            if container.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { missing.append("期刊 / 会议") }
        }
        return missing
    }

    var headerValues: [(String, String)] {
        [
            ("reference_type", kind.rawValue),
            ("reference_authors", authors), ("reference_year", year),
            ("reference_container", container), ("reference_publisher", publisher),
            ("reference_volume", volume), ("reference_issue", issue),
            ("reference_pages", pages), ("reference_doi", doi),
            ("reference_url", url), ("reference_isbn", isbn),
        ].filter { !$0.1.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }
}

/// A deliberately small local formatter for the common single-item cases.
/// Full document-wide citation management needs a CSL processor and document integration.
enum CapsuleReferenceFormatter {
    static func bibliography(title: String, reference: CapsuleReference,
                             style: ScholayCitationStyle) -> String? {
        guard reference.missingFields.isEmpty,
              !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        let names = reference.authors.split(separator: ";")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        guard !names.isEmpty else { return nil }
        let authors = names.joined(separator: ", ")
        let year = reference.year.trimmingCharacters(in: .whitespacesAndNewlines)
        let title = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let source = reference.kind == .book || reference.kind == .report
            ? reference.publisher : reference.container
        let detail = [reference.volume, reference.issue.isEmpty ? "" : "(\(reference.issue))",
                      reference.pages.isEmpty ? "" : ": \(reference.pages)"]
            .joined()
        let locator: String
        if !reference.doi.isEmpty {
            let doi = reference.doi.replacingOccurrences(of: "https://doi.org/", with: "")
            locator = "https://doi.org/\(doi)"
        } else { locator = reference.url }
        let suffix = locator.isEmpty ? "" : " \(locator)"
        switch style {
        case .gbT7714:
            let marker: String
            switch reference.kind {
            case .book: marker = "M"
            case .journalArticle: marker = "J"
            case .conferencePaper: marker = "C"
            case .report: marker = "R"
            }
            return "\(authors). \(title)[\(marker)]. \(source), \(year)\(detail.isEmpty ? "" : ", \(detail)").\(suffix)"
        case .apa7:
            return "\(authors). (\(year)). \(title). \(source)\(detail.isEmpty ? "" : ", \(detail)").\(suffix)"
        case .mla9:
            return "\(authors). \"\(title).\" \(source), \(year)\(detail.isEmpty ? "" : ", \(detail)").\(suffix)"
        case .chicagoAuthorDate:
            return "\(authors). \(year). \"\(title).\" \(source)\(detail.isEmpty ? "" : ", \(detail)").\(suffix)"
        case .vancouver:
            return "\(authors). \(title). \(source). \(year)\(detail.isEmpty ? "" : ";\(detail)").\(suffix)"
        case .ieee:
            return "\(authors), \"\(title),\" \(source), \(year)\(detail.isEmpty ? "" : ", \(detail)").\(suffix)"
        }
    }
}

enum CapsuleReferencePreferences {
    private static let styleKey = "\(RimesIdentity.preferenceKeyPrefix)Capsule.referenceStyle.v1"

    static var style: ScholayCitationStyle {
        get { ScholayCitationStyle(rawValue: UserDefaults.standard.string(forKey: styleKey) ?? "") ?? .gbT7714 }
        set { UserDefaults.standard.set(newValue.rawValue, forKey: styleKey) }
    }
}

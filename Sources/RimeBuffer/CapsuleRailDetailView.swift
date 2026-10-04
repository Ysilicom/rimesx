import AppKit
import Carbon.HIToolbox

enum CapsuleRailDetailPolicy {
    /// How long a verified password stays open in the rail.
    static let passwordLease: TimeInterval = 60
    /// How long one secret field stays visible after "show".
    static let secretRevealDuration: TimeInterval = 15
}

/// The entry opened from a card by a double-click: title and summary on the
/// left, and on the right every field, line and block as its own row that
/// copies on its own. A password shows nothing until its passcode is verified,
/// and even then secret values stay masked unless shown one at a time.
final class CapsuleRailDetailView: NSView {
    enum Mode: Equatable { case note, password }

    let entry: CapsuleRailEntry
    let mode: Mode
    var onClose: (() -> Void)?
    /// Called once a password verifies, so the rail can update its hints.
    var onUnlock: (() -> Void)?
    /// Notes only: puts one row's text into the target field.
    var onInsert: ((String) -> Bool)?
    /// Copies one row. `secret` copies carry the concealed markers and clear.
    var onCopy: ((String, _ secret: Bool) -> Bool)?

    private let readSecret: () throws -> String
    private let presentationAllowed: () -> Bool
    private let verifier: CapsuleInlinePasscodeView?
    private let infoPanel = NSView()
    private let backButton = RimePointingHandButton(title: "返回", target: nil, action: nil)
    private let iconView = NSImageView()
    private let titleLabel = NSTextField(wrappingLabelWithString: "")
    private let summaryLabel = NSTextField(wrappingLabelWithString: "")
    private let noticeLabel = NSTextField(wrappingLabelWithString: "")
    private let scrollView = NSScrollView()
    private let rowsView = CapsuleDetailRowsDocument()
    private var rowViews: [CapsuleDetailRowView] = []
    private var selectedIndex: Int?
    private var leaseTimer: Timer?
    private var tickTimer: Timer?
    private var observers: [NSObjectProtocol] = []
    private var workspaceObservers: [NSObjectProtocol] = []
    private(set) var isUnlocked: Bool
    private var ended = false
    private var contentHeight: CGFloat = 0

    init(entry: CapsuleRailEntry,
         passcodeStore: CapsuleRevealPasscodeStore,
         readSecret: @escaping () throws -> String = { throw CapsuleWindowRepositoryError.staleRecord },
         presentationAllowed: @escaping () -> Bool = { true }) {
        self.entry = entry
        mode = entry.kind == .password ? .password : .note
        self.readSecret = readSecret
        self.presentationAllowed = presentationAllowed
        verifier = entry.kind == .password
            ? CapsuleInlinePasscodeView(store: passcodeStore, railPresentation: true) : nil
        isUnlocked = entry.kind != .password
        super.init(frame: .zero)
        wantsLayer = true
        configure()
        if mode == .note {
            showRows(CapsuleEntryGrammar.sections(
                body: entry.formatIssue == nil ? (entry.payload ?? "") : "",
                headerFields: entry.headerFields
            ) + rawSection())
        } else if let verifier {
            verifier.onVerified = { [weak self] in self?.unlock() }
            verifier.onCancel = { [weak self] in self?.close() }
            verifier.translatesAutoresizingMaskIntoConstraints = false
            addSubview(verifier)
            addSubview(backButton, positioned: .above, relativeTo: verifier)
            NSLayoutConstraint.activate([
                verifier.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor),
                verifier.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor),
                verifier.topAnchor.constraint(equalTo: topAnchor),
                verifier.bottomAnchor.constraint(equalTo: bottomAnchor),
            ])
        }
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError() }

    deinit {
        leaseTimer?.invalidate()
        tickTimer?.invalidate()
        observers.forEach(NotificationCenter.default.removeObserver)
        workspaceObservers.forEach(NSWorkspace.shared.notificationCenter.removeObserver)
    }

    override var acceptsFirstResponder: Bool { mode == .password && isUnlocked }
    /// Clicks on the panel's own background must not reach the rail, which
    /// treats a background click as "close anything open".
    override func mouseDown(with event: NSEvent) {}
    override func acceptsFirstMouse(for event: NSEvent?) -> Bool { true }

    /// Passwords: put keyboard focus in the passcode field.
    func focus() { verifier?.focus() }

    // MARK: Keyboard

    /// Rail keys while the detail is open. Returns false for keys the rail
    /// itself should still see (Tab switches tabs, which closes the detail).
    func handleKey(_ event: NSEvent) -> Bool {
        let modifiers = event.modifierFlags.intersection([.command, .control, .option, .shift])
        if modifiers == [.command], event.keyCode == UInt16(kVK_ANSI_C) {
            copySelected()
            return true
        }
        guard modifiers.isEmpty || modifiers == [.shift] else { return mode == .password }
        switch Int(event.keyCode) {
        case kVK_Escape: close(); return true
        case kVK_UpArrow: moveSelection(-1); return true
        case kVK_DownArrow: moveSelection(1); return true
        case kVK_LeftArrow, kVK_RightArrow: return true
        case kVK_Return, kVK_ANSI_KeypadEnter: activateSelected(); return true
        case kVK_Space where mode == .password: toggleRevealSelected(); return true
        case kVK_Tab: return false
        default: return mode == .password
        }
    }

    override func keyDown(with event: NSEvent) {
        if !handleKey(event) { super.keyDown(with: event) }
    }

    override func performKeyEquivalent(with event: NSEvent) -> Bool {
        guard mode == .password, isUnlocked, window?.firstResponder === self else {
            return super.performKeyEquivalent(with: event)
        }
        return handleKey(event)
    }

    override func resignFirstResponder() -> Bool {
        // A verified password never outlives the keyboard focus it was read under.
        if mode == .password, isUnlocked { DispatchQueue.main.async { [weak self] in self?.close() } }
        return super.resignFirstResponder()
    }

    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        observers.forEach(NotificationCenter.default.removeObserver)
        observers = []
        workspaceObservers.forEach(NSWorkspace.shared.notificationCenter.removeObserver)
        workspaceObservers = []
        guard let window else { close(); return }
        guard mode == .password else { return }
        for name in [NSWindow.didResignKeyNotification, NSWindow.willCloseNotification] {
            observers.append(NotificationCenter.default.addObserver(
                forName: name, object: window, queue: .main
            ) { [weak self] _ in self?.close() })
        }
        // A changed passcode or record, leaving the app, a session switch or
        // sleep all revoke what was verified.
        for name in [Notification.Name.capsuleRevealPasscodeDidChange, .capsuleStoreDidChange,
                     NSApplication.didResignActiveNotification] {
            observers.append(NotificationCenter.default.addObserver(
                forName: name, object: nil, queue: .main
            ) { [weak self] _ in self?.close() })
        }
        for name in [NSWorkspace.sessionDidResignActiveNotification, NSWorkspace.willSleepNotification] {
            workspaceObservers.append(NSWorkspace.shared.notificationCenter.addObserver(
                forName: name, object: nil, queue: .main
            ) { [weak self] _ in self?.close() })
        }
    }

    // MARK: Actions

    func close() {
        guard !ended else { return }
        ended = true
        leaseTimer?.invalidate()
        tickTimer?.invalidate()
        verifier?.reset()
        rowViews.forEach { $0.scrub() }
        rowViews = []
        isUnlocked = false
        onClose?()
    }

    @objc private func backPressed() { close() }

    private func unlock() {
        guard !ended, mode == .password, window?.isKeyWindow == true,
              !IsSecureEventInputEnabled(), presentationAllowed() else { close(); return }
        do {
            let body = try readSecret()
            guard !ended, presentationAllowed() else { close(); return }
            verifier?.removeFromSuperview()
            isUnlocked = true
            showRows(CapsuleEntryGrammar.sections(body: body))
            window?.makeFirstResponder(self)
            onUnlock?()
            let lease = Timer(timeInterval: CapsuleRailDetailPolicy.passwordLease, repeats: false) {
                [weak self] _ in self?.close()
            }
            leaseTimer = lease
            RunLoop.main.add(lease, forMode: .common)
            updateNotice()
        } catch {
            // Never surface store diagnostics or record contents here.
            NSSound.beep()
            close()
        }
    }

    private func activateSelected() {
        guard let index = selectedIndex, rowViews.indices.contains(index) else { return }
        activate(rowViews[index])
    }

    private func activate(_ row: CapsuleDetailRowView) {
        if mode == .note, let onInsert {
            if !onInsert(row.copyText) { NSSound.beep() }
        } else {
            copy(row)
        }
    }

    private func copySelected() {
        guard let index = selectedIndex, rowViews.indices.contains(index) else {
            NSSound.beep(); return
        }
        copy(rowViews[index])
    }

    private func copy(_ row: CapsuleDetailRowView) {
        guard !ended, isUnlocked, mode == .note || presentationAllowed() else { NSSound.beep(); return }
        if onCopy?(row.copyText, mode == .password) == true {
            row.flashCopied()
        } else {
            NSSound.beep()
        }
    }

    private func toggleRevealSelected() {
        guard let index = selectedIndex, rowViews.indices.contains(index) else { return }
        rowViews[index].toggleReveal()
    }

    private func moveSelection(_ delta: Int) {
        guard !rowViews.isEmpty else { return }
        let next = min(max((selectedIndex ?? -1) + delta, 0), rowViews.count - 1)
        select(next)
    }

    private func select(_ index: Int) {
        selectedIndex = index
        for (position, row) in rowViews.enumerated() { row.isSelected = position == index }
        rowsView.scrollToVisible(rowViews[index].frame.insetBy(dx: 0, dy: -4))
    }

    // MARK: Layout

    private func configure() {
        infoPanel.wantsLayer = true
        infoPanel.layer?.cornerRadius = 8
        infoPanel.layer?.backgroundColor = RimeUI.surface2.cgColor
        infoPanel.translatesAutoresizingMaskIntoConstraints = false
        addSubview(infoPanel)

        backButton.identifier = NSUserInterfaceItemIdentifier("capsule-detail-back")
        backButton.image = RimeUI.symbol("chevron.left", pointSize: 10, weight: .semibold)
        backButton.imagePosition = .imageLeading
        backButton.isBordered = false
        backButton.font = .systemFont(ofSize: 11, weight: .medium)
        backButton.contentTintColor = RimeUI.textSecondary
        backButton.target = self
        backButton.action = #selector(backPressed)
        backButton.toolTip = "返回卡片列表"
        backButton.setAccessibilityLabel("返回卡片列表")
        backButton.translatesAutoresizingMaskIntoConstraints = false
        addSubview(backButton)

        iconView.image = RimeUI.symbol(mode == .password ? "lock" : "note.text", pointSize: 11, weight: .semibold)
        iconView.image?.isTemplate = true
        iconView.contentTintColor = RimeUI.textSecondary
        titleLabel.stringValue = entry.title
        titleLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        titleLabel.textColor = RimeUI.textPrimary
        titleLabel.maximumNumberOfLines = 2
        titleLabel.lineBreakMode = .byTruncatingTail
        summaryLabel.stringValue = entry.preview
        summaryLabel.font = .systemFont(ofSize: 11)
        summaryLabel.textColor = RimeUI.textSecondary
        summaryLabel.maximumNumberOfLines = 3
        summaryLabel.lineBreakMode = .byTruncatingTail
        noticeLabel.font = .systemFont(ofSize: 10, weight: .medium)
        noticeLabel.textColor = RimeUI.brandYellow
        noticeLabel.maximumNumberOfLines = 3
        for view in [iconView, titleLabel, summaryLabel, noticeLabel] as [NSView] {
            view.translatesAutoresizingMaskIntoConstraints = false
            infoPanel.addSubview(view)
        }
        scrollView.drawsBackground = false
        scrollView.hasVerticalScroller = true
        scrollView.autohidesScrollers = true
        scrollView.documentView = rowsView
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(scrollView)

        NSLayoutConstraint.activate([
            infoPanel.leadingAnchor.constraint(equalTo: leadingAnchor),
            infoPanel.topAnchor.constraint(equalTo: topAnchor),
            infoPanel.bottomAnchor.constraint(equalTo: bottomAnchor),
            infoPanel.widthAnchor.constraint(equalToConstant: ClipboardHistoryWindowMetrics.cardWidth),
            iconView.leadingAnchor.constraint(equalTo: infoPanel.leadingAnchor, constant: 10),
            iconView.topAnchor.constraint(equalTo: infoPanel.topAnchor, constant: 10),
            titleLabel.leadingAnchor.constraint(equalTo: iconView.trailingAnchor, constant: 5),
            titleLabel.trailingAnchor.constraint(equalTo: infoPanel.trailingAnchor, constant: -10),
            titleLabel.topAnchor.constraint(equalTo: infoPanel.topAnchor, constant: 8),
            summaryLabel.leadingAnchor.constraint(equalTo: infoPanel.leadingAnchor, constant: 10),
            summaryLabel.trailingAnchor.constraint(equalTo: infoPanel.trailingAnchor, constant: -10),
            summaryLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 4),
            noticeLabel.leadingAnchor.constraint(equalTo: infoPanel.leadingAnchor, constant: 10),
            noticeLabel.trailingAnchor.constraint(equalTo: infoPanel.trailingAnchor, constant: -10),
            noticeLabel.bottomAnchor.constraint(equalTo: infoPanel.bottomAnchor, constant: -8),
            backButton.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -4),
            backButton.topAnchor.constraint(equalTo: topAnchor, constant: 3),
            backButton.widthAnchor.constraint(equalToConstant: 72),
            backButton.heightAnchor.constraint(equalToConstant: 24),
            scrollView.leadingAnchor.constraint(equalTo: infoPanel.trailingAnchor, constant: 8),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor),
            scrollView.topAnchor.constraint(equalTo: backButton.bottomAnchor, constant: 5),
            scrollView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
        addSubview(backButton, positioned: .above, relativeTo: scrollView)
        updateNotice()
    }

    private func updateNotice() {
        if let issue = entry.formatIssue {
            noticeLabel.stringValue = issue
        } else if mode == .note,
                  CapsuleEntryGrammar.mayContainSecret(body: entry.payload ?? "", headerFields: entry.headerFields) {
            noticeLabel.stringValue = "这条笔记里可能有密码。建议用卡片菜单「迁移为密码…」加密保存。"
        } else if mode == .password {
            // Verification itself shows no instruction text (2026-09-22).
            noticeLabel.stringValue = isUnlocked
                ? "已验证 · \(Int(CapsuleRailDetailPolicy.passwordLease)) 秒后自动关闭"
                : ""
        } else {
            noticeLabel.stringValue = ""
        }
    }

    /// A note that could not be parsed shows its raw text, one line per row.
    private func rawSection() -> [CapsuleDetailSection] {
        guard entry.formatIssue != nil, let raw = entry.payload else { return [] }
        let rows = raw.split(separator: "\n").map { CapsuleDetailRow.line(String($0)) }
        return [CapsuleDetailSection(heading: "原文", rows: rows)]
    }

    private func showRows(_ sections: [CapsuleDetailSection]) {
        rowsView.subviews.forEach { $0.removeFromSuperview() }
        rowViews = []
        var y: CGFloat = 0
        let width = max(200, scrollView.contentSize.width)
        for section in sections {
            if let heading = section.heading {
                let label = NSTextField(labelWithString: heading)
                label.font = .systemFont(ofSize: 10, weight: .semibold)
                label.textColor = RimeUI.textMuted
                label.lineBreakMode = .byTruncatingTail
                label.frame = NSRect(x: 6, y: y + 3, width: width - 12, height: 14)
                label.autoresizingMask = [.width]
                rowsView.addSubview(label)
                y += 18
            }
            for row in section.rows {
                let view = CapsuleDetailRowView(row: row, protected: mode == .password)
                view.frame = NSRect(x: 0, y: y, width: width, height: CapsuleDetailRowView.height)
                view.autoresizingMask = [.width]
                let index = rowViews.count
                view.onSelect = { [weak self] in self?.select(index) }
                view.onActivate = { [weak self, weak view] in
                    guard let self, let view else { return }
                    self.select(index)
                    self.activate(view)
                }
                view.onCopy = { [weak self, weak view] in
                    guard let self, let view else { return }
                    self.select(index)
                    self.copy(view)
                }
                rowsView.addSubview(view)
                rowViews.append(view)
                y += CapsuleDetailRowView.height
            }
        }
        if rowViews.isEmpty {
            let empty = NSTextField(labelWithString: "没有可复制的内容")
            empty.font = .systemFont(ofSize: 11)
            empty.textColor = RimeUI.textMuted
            empty.frame = NSRect(x: 6, y: 6, width: width - 12, height: 16)
            rowsView.addSubview(empty)
            y = 28
        }
        contentHeight = y
        rowsView.frame = NSRect(x: 0, y: 0, width: width, height: max(y, scrollView.contentSize.height))
        if !rowViews.isEmpty { select(0) }
        if rowViews.contains(where: \.isTOTP) {
            let tick = Timer(timeInterval: 1, repeats: true) { [weak self] _ in
                self?.rowViews.forEach { $0.refreshTOTP() }
            }
            tickTimer = tick
            RunLoop.main.add(tick, forMode: .common)
        }
    }

    override func layout() {
        super.layout()
        let size = scrollView.contentSize
        let target = NSSize(width: max(1, size.width), height: max(contentHeight, size.height))
        guard size.width > 0, rowsView.frame.size != target else { return }
        rowsView.setFrameSize(target)
        // Rows may have been built before this view had a width.
        for view in rowsView.subviews {
            let inset: CGFloat = view is CapsuleDetailRowView ? 0 : 6
            view.frame = NSRect(x: inset, y: view.frame.minY,
                                width: target.width - inset * 2, height: view.frame.height)
            view.needsLayout = true
        }
    }

    // MARK: Smoke

    var rowCopyTextsForSmoke: [String] { rowViews.map(\.copyText) }
    var renderedValuesForSmoke: [String] { rowViews.map(\.renderedValue) }
    var noticeForSmoke: String { noticeLabel.stringValue }
    var selectedIndexForSmoke: Int? { selectedIndex }
}

private final class CapsuleDetailRowsDocument: NSView {
    override var isFlipped: Bool { true }
}

/// One copyable row. Secret values are drawn masked; the plaintext never
/// becomes a tooltip or an accessibility value.
private final class CapsuleDetailRowView: NSView {
    static let height: CGFloat = 24

    var onSelect: (() -> Void)?
    var onActivate: (() -> Void)?
    var onCopy: (() -> Void)?
    var isSelected = false { didSet { refreshBackground() } }

    private let row: CapsuleDetailRow
    private let protected: Bool
    private let labelField = NSTextField(labelWithString: "")
    private let valueField = NSTextField(labelWithString: "")
    private let revealButton = CapsuleDetailButton(title: "", target: nil, action: nil)
    private let copyButton = CapsuleDetailButton(title: "", target: nil, action: nil)
    private var revealed = false
    private var revealTimer: Timer?
    private var copiedTimer: Timer?
    private let totp: CapsuleTOTP.Parameters?

    init(row: CapsuleDetailRow, protected: Bool) {
        self.row = row
        self.protected = protected
        if case let .field(field) = row {
            totp = CapsuleTOTP.parameters(label: field.label, value: field.value)
        } else {
            totp = nil
        }
        super.init(frame: .zero)
        wantsLayer = true
        layer?.cornerRadius = 5
        configure()
        render()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError() }

    deinit {
        revealTimer?.invalidate()
        copiedTimer?.invalidate()
    }

    var isTOTP: Bool { totp != nil }

    private var isSecret: Bool {
        guard protected, case let .field(field) = row else { return false }
        return field.isSecret
    }

    /// TOTP rows copy the current code, never the seed.
    var copyText: String {
        if let totp { return CapsuleTOTP.code(totp, at: Date()).code }
        return row.copyText
    }

    var renderedValue: String { valueField.stringValue }

    override func acceptsFirstMouse(for event: NSEvent?) -> Bool { true }

    override func mouseDown(with event: NSEvent) {
        if event.clickCount >= 2 { onActivate?() } else { onSelect?() }
    }

    func toggleReveal() {
        guard isSecret else { return }
        revealed.toggle()
        revealTimer?.invalidate()
        if revealed {
            let timer = Timer(timeInterval: CapsuleRailDetailPolicy.secretRevealDuration, repeats: false) {
                [weak self] _ in
                self?.revealed = false
                self?.render()
            }
            revealTimer = timer
            RunLoop.main.add(timer, forMode: .common)
        }
        render()
    }

    func refreshTOTP() { if totp != nil { render() } }

    func flashCopied() {
        copyButton.image = RimeUI.symbol("checkmark", pointSize: 10, weight: .bold)
        copiedTimer?.invalidate()
        let timer = Timer(timeInterval: 1.2, repeats: false) { [weak self] _ in
            self?.copyButton.image = RimeUI.symbol("doc.on.doc", pointSize: 10, weight: .semibold)
        }
        copiedTimer = timer
        RunLoop.main.add(timer, forMode: .common)
    }

    func scrub() {
        revealTimer?.invalidate()
        revealed = false
        labelField.stringValue = ""
        valueField.stringValue = ""
    }

    private func configure() {
        labelField.font = .systemFont(ofSize: 11)
        labelField.textColor = RimeUI.textSecondary
        labelField.lineBreakMode = .byTruncatingTail
        valueField.font = { if case .code = row { return .monospacedSystemFont(ofSize: 11, weight: .regular) }
            return .systemFont(ofSize: 12) }()
        valueField.textColor = RimeUI.textPrimary
        valueField.lineBreakMode = .byTruncatingTail
        valueField.maximumNumberOfLines = 1
        if protected { valueField.setAccessibilityElement(false) }
        for button in [revealButton, copyButton] {
            button.isBordered = false
            button.imagePosition = .imageOnly
            button.target = self
            button.contentTintColor = RimeUI.textSecondary
        }
        revealButton.action = #selector(revealPressed)
        revealButton.toolTip = "显示 15 秒"
        revealButton.setAccessibilityLabel("显示")
        revealButton.isHidden = !isSecret
        copyButton.action = #selector(copyPressed)
        copyButton.image = RimeUI.symbol("doc.on.doc", pointSize: 10, weight: .semibold)
        copyButton.toolTip = "复制"
        copyButton.setAccessibilityLabel(isSecret ? "复制（不显示）" : "复制")
        for view in [labelField, valueField, revealButton, copyButton] as [NSView] {
            view.translatesAutoresizingMaskIntoConstraints = false
            addSubview(view)
        }
        let hasLabel: Bool = { if case .field = row { return true }; return false }()
        NSLayoutConstraint.activate([
            labelField.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 6),
            labelField.centerYAnchor.constraint(equalTo: centerYAnchor),
            labelField.widthAnchor.constraint(equalToConstant: hasLabel ? 92 : 0),
            valueField.leadingAnchor.constraint(equalTo: labelField.trailingAnchor, constant: hasLabel ? 8 : 0),
            valueField.centerYAnchor.constraint(equalTo: centerYAnchor),
            valueField.trailingAnchor.constraint(lessThanOrEqualTo: revealButton.leadingAnchor, constant: -4),
            revealButton.trailingAnchor.constraint(equalTo: copyButton.leadingAnchor, constant: -2),
            revealButton.centerYAnchor.constraint(equalTo: centerYAnchor),
            revealButton.widthAnchor.constraint(equalToConstant: 20),
            copyButton.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -4),
            copyButton.centerYAnchor.constraint(equalTo: centerYAnchor),
            copyButton.widthAnchor.constraint(equalToConstant: 20),
        ])
    }

    private func render() {
        switch row {
        case let .field(field):
            labelField.stringValue = field.label
            labelField.toolTip = field.label
            if let totp {
                let (code, remaining) = CapsuleTOTP.code(totp, at: Date())
                let half = code.count / 2
                valueField.stringValue = "\(code.prefix(half)) \(code.dropFirst(half))  · \(remaining)s"
            } else if isSecret, !revealed {
                valueField.stringValue = "••••••••"
            } else {
                valueField.stringValue = field.value
            }
            valueField.toolTip = isSecret || totp != nil || protected ? nil : field.value
        case let .line(text):
            valueField.stringValue = text
            valueField.toolTip = protected ? nil : text
        case let .code(text):
            let lines = text.split(separator: "\n", omittingEmptySubsequences: false)
            valueField.stringValue = lines.count > 1 ? "\(lines[0])  …（\(lines.count) 行）" : text
            valueField.toolTip = protected ? nil : text
        }
        revealButton.image = RimeUI.symbol(revealed ? "eye.slash" : "eye", pointSize: 10, weight: .semibold)
        revealButton.toolTip = revealed ? "隐藏" : "显示 15 秒"
    }

    private func refreshBackground() {
        layer?.backgroundColor = isSelected
            ? RimeUI.accentSecondary.withAlphaComponent(0.22).cgColor
            : NSColor.clear.cgColor
    }

    @objc private func revealPressed() {
        onSelect?()
        toggleReveal()
    }

    @objc private func copyPressed() { onCopy?() }
}

/// Buttons inside the detail never take keyboard focus: a verified password
/// closes when its view loses focus, and a click must not do that.
private final class CapsuleDetailButton: ClipboardFirstMouseButton {
    override var acceptsFirstResponder: Bool { false }
}

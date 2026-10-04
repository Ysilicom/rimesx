import AppKit

/// The vertical filter buttons on the left of the Capsule rail's card band:
/// one per filter of the current module (全部, 文本, 图片, …), so a module's
/// entries can be narrowed without opening the manager.
final class CapsuleRailFilterColumn: NSView {
    static let width: CGFloat = 60
    static let rowHeight: CGFloat = 18
    static let rowSpacing: CGFloat = 3

    var onSelect: ((CapsuleModuleFilter) -> Void)?
    private(set) var filters: [CapsuleModuleFilter] = []
    private(set) var selected: CapsuleModuleFilter = .all
    private var buttons: [CapsuleRailFilterButton] = []
    private let stack = NSStackView()

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        translatesAutoresizingMaskIntoConstraints = false
        stack.orientation = .vertical
        stack.alignment = .leading
        stack.spacing = Self.rowSpacing
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 2),
        ])
        setAccessibilityRole(.group)
        setAccessibilityLabel("筛选")
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError() }

    func update(filters: [CapsuleModuleFilter], selected: CapsuleModuleFilter) {
        if filters != self.filters {
            self.filters = filters
            buttons.forEach { $0.removeFromSuperview() }
            buttons = filters.map { filter in
                let button = CapsuleRailFilterButton(title: filter.title, target: self,
                                                     action: #selector(pressed(_:)))
                button.filter = filter
                button.translatesAutoresizingMaskIntoConstraints = false
                button.widthAnchor.constraint(equalToConstant: Self.width).isActive = true
                button.heightAnchor.constraint(equalToConstant: Self.rowHeight).isActive = true
                stack.addArrangedSubview(button)
                return button
            }
        }
        self.selected = selected
        applyAppearance()
    }

    func applyAppearance() {
        for button in buttons {
            let isSelected = button.filter == selected
            button.layer?.backgroundColor = isSelected
                ? RimeUI.surface3.cgColor : NSColor.clear.cgColor
            let paragraph = NSMutableParagraphStyle()
            paragraph.firstLineHeadIndent = 8
            paragraph.headIndent = 8
            paragraph.lineBreakMode = .byTruncatingTail
            button.attributedTitle = NSAttributedString(string: button.filter.title, attributes: [
                .font: NSFont.systemFont(ofSize: 11, weight: isSelected ? .semibold : .regular),
                .foregroundColor: isSelected ? RimeUI.textPrimary : RimeUI.textSecondary,
                .paragraphStyle: paragraph,
            ])
            button.setAccessibilityValue(isSelected ? "已选" : nil)
        }
    }

    @objc private func pressed(_ sender: CapsuleRailFilterButton) {
        onSelect?(sender.filter)
    }

    var buttonTitlesForSmoke: [String] { buttons.map(\.filter.title) }
}

/// Filter buttons act on first click and never take keyboard focus: the rail
/// is a nonactivating panel whose keys come through the input method.
final class CapsuleRailFilterButton: ClipboardFirstMouseButton {
    var filter: CapsuleModuleFilter = .all

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        isBordered = false
        wantsLayer = true
        layer?.cornerRadius = 5
        alignment = .left
        focusRingType = .none
        imagePosition = .noImage
    }

    convenience init(title: String, target: AnyObject, action: Selector) {
        self.init(frame: .zero)
        self.title = title
        self.target = target
        self.action = action
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError() }

    override var acceptsFirstResponder: Bool { false }
}

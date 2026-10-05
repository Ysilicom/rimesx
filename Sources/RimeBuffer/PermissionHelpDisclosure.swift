import AppKit

/// Recovery stays available without making it part of the first-use path.
final class PermissionHelpDisclosure: NSStackView {
    private let details: NSView
    private let button: CaptureButton
    var didToggle: (() -> Void)?
    private(set) var expanded = false

    init(_ title: String = "遇到问题", details: NSView, width: CGFloat) {
        self.details = details
        button = CaptureButton(title, symbol: "chevron.right", action: {})
        super.init(frame: .zero)
        orientation = .vertical; alignment = .leading; spacing = 12
        detachesHiddenViews = true
        button.font = .systemFont(ofSize: 13)
        button.perform = { [weak self] in self?.setExpanded(self?.expanded != true) }
        details.isHidden = true
        addArrangedSubview(button); addArrangedSubview(details)
        translatesAutoresizingMaskIntoConstraints = false
        widthAnchor.constraint(equalToConstant: width).isActive = true
        details.translatesAutoresizingMaskIntoConstraints = false
        details.widthAnchor.constraint(equalTo: widthAnchor).isActive = true
    }
    required init?(coder: NSCoder) { fatalError() }
    func setExpanded(_ expanded: Bool) {
        self.expanded = expanded; details.isHidden = !expanded
        button.image = RimeUI.symbol(expanded ? "chevron.down" : "chevron.right", pointSize: 12, weight: .regular)
        button.setAccessibilityValue(expanded ? "已展开" : "已收起")
        needsLayout = true; didToggle?()
    }
}

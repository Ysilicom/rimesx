import UIKit
import RimesCore

final class NineKeySpellingStrip: UIView {
    var onSelect: ((String) -> Void)?
    private let scroll = UIScrollView(), row = UIStackView()
    private var spellings: [String] = []
    override init(frame: CGRect) {
        super.init(frame: frame)
        scroll.showsHorizontalScrollIndicator = false; row.axis = .horizontal; row.spacing = 6
        if #available(iOS 26, *) {
            scroll.topEdgeEffect.isHidden = true; scroll.bottomEdgeEffect.isHidden = true
            scroll.leftEdgeEffect.isHidden = true; scroll.rightEdgeEffect.isHidden = true
        }
        addSubview(scroll); scroll.addSubview(row)
        accessibilityIdentifier = "keyboard.nineKey.spellings"
    }
    required init?(coder: NSCoder) { fatalError() }
    func update(_ values: [String]) {
        guard values != spellings else { return }; spellings = values
        row.arrangedSubviews.forEach { row.removeArrangedSubview($0); $0.removeFromSuperview() }
        for spelling in values {
            let button = UIButton(type: .system)
            button.setTitle(spelling, for: .normal); button.titleLabel?.font = .systemFont(ofSize: 17)
            button.setTitleColor(.label, for: .normal)
            button.accessibilityIdentifier = "keyboard.nineKey.spelling.\(spelling)"
            button.accessibilityLabel = L("选择拼音 \(spelling)", "Choose spelling \(spelling)")
            button.addAction(UIAction { [weak self] _ in self?.onSelect?(spelling) }, for: .touchUpInside)
            button.widthAnchor.constraint(greaterThanOrEqualToConstant: max(44, CGFloat(spelling.count) * 10 + 16)).isActive = true
            row.addArrangedSubview(button)
        }
        scroll.contentOffset = .zero; setNeedsLayout()
    }
    override func layoutSubviews() {
        super.layoutSubviews(); scroll.frame = bounds
        let size = row.systemLayoutSizeFitting(UIView.layoutFittingCompressedSize)
        row.frame = CGRect(x: 0, y: 0, width: size.width, height: bounds.height)
        scroll.contentSize = CGSize(width: max(bounds.width, size.width), height: bounds.height)
    }
}

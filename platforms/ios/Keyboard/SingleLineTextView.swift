import UIKit
import RimesCore

/// One line that never wraps; drag horizontally to read the rest. Optional
/// block backgrounds and an overlay caret never change the text's own layout.
final class SingleLineTextView: UIScrollView {
    private let label = UILabel()
    private let caret = UIView()
    private var blockViews: [UIView] = []
    private var plain: String?
    private var focus: NSRange?
    private var followedSignature = ""
    private static let inset: CGFloat = 8
    /// Input accepts the typed text (outlined, with caret); output only displays.
    enum Role { case input, output }
    var role = Role.input { didSet { applyRole(); if let plain { text = plain } } }
    var font: UIFont = .systemFont(ofSize: 15) { didSet { if let plain, oldValue != font { text = plain } } }
    var text: String {
        get { label.attributedText?.string ?? "" }
        set {
            let value = NSMutableAttributedString(string: newValue, attributes: [.font: font, .foregroundColor: role == .input ? UIColor.label : UIColor.secondaryLabel])
            BufferBlockStyle.apply(to: value, ranges: blockRanges)
            label.attributedText = value; plain = newValue; setNeedsLayout()
        }
    }
    var attributedText: NSAttributedString? {
        get { label.attributedText }
        set { plain = nil; label.attributedText = newValue; setNeedsLayout() }
    }
    /// Displayed UTF-16 block ranges and the emphasised block; set before the text.
    private(set) var blockRanges: [NSRange] = []
    private(set) var activeBlock: Int?
    func setBlocks(_ ranges: [NSRange], active: Int?) {
        blockRanges = ranges; activeBlock = active; setNeedsLayout()
    }
    /// UTF-16 caret position, shown only for the input role.
    var caretLocation: Int? { didSet { if caretLocation != oldValue { restartBlink() }; setNeedsLayout() } }
    override init(frame: CGRect) {
        super.init(frame: frame)
        label.numberOfLines = 1; label.lineBreakMode = .byClipping
        addSubview(label)
        caret.backgroundColor = .systemTeal; caret.layer.cornerRadius = 1; caret.isUserInteractionEnabled = false
        addSubview(caret)
        showsHorizontalScrollIndicator = false; showsVerticalScrollIndicator = false
        alwaysBounceHorizontal = false; alwaysBounceVertical = false; bounces = true
        layer.cornerRadius = 7; layer.cornerCurve = .continuous
        applyRole()
        registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (view: SingleLineTextView, _: UITraitCollection) in view.applyRole(); view.setNeedsLayout() }
    }
    required init?(coder: NSCoder) { fatalError() }
    private func applyRole() {
        switch role {
        case .input:
            backgroundColor = .systemBackground
            layer.borderWidth = 1; layer.borderColor = UIColor.systemTeal.withAlphaComponent(0.7).resolvedColor(with: traitCollection).cgColor
            accessibilityTraits = [.updatesFrequently]
        case .output:
            backgroundColor = .tertiarySystemFill
            layer.borderWidth = 0
            accessibilityTraits = [.staticText, .notEnabled]
        }
    }
    private func restartBlink() {
        caret.layer.removeAnimation(forKey: "blink")
        let blink = CAKeyframeAnimation(keyPath: "opacity")
        blink.values = [1, 1, 0, 0]; blink.keyTimes = [0, 0.5, 0.55, 1]
        blink.duration = 1.0; blink.repeatCount = .infinity; blink.beginTime = CACurrentMediaTime() + 0.5
        caret.layer.add(blink, forKey: "blink")
    }
    /// Keeps a UTF-16 range in view, only when text or range changed, so a
    /// manual drag stays where the user left it until the content moves on.
    func scrollRangeToVisible(_ range: NSRange) { focus = range; setNeedsLayout() }
    /// X of the boundary before `index`. Ends (and the caret) stop before a block
    /// gap that follows the previous character; starts sit after it.
    private func x(at index: Int, in text: NSAttributedString, beforeGap: Bool = true) -> CGFloat {
        let end = max(0, min(index, text.length))
        var width = text.attributedSubstring(from: NSRange(location: 0, length: end)).size().width
        if beforeGap, end > 0, let kern = text.attribute(.kern, at: end - 1, effectiveRange: nil) as? CGFloat { width -= kern }
        return Self.inset + width
    }
    /// Frames of the block backgrounds in content coordinates, for tests.
    private(set) var blockFrames: [CGRect] = []
    override func layoutSubviews() {
        super.layoutSubviews()
        let width = ceil(label.sizeThatFits(CGSize(width: CGFloat.greatestFiniteMagnitude, height: bounds.height)).width)
        label.frame = CGRect(x: Self.inset, y: 0, width: width, height: bounds.height)
        contentSize = CGSize(width: max(bounds.width, width + 2 * Self.inset), height: bounds.height)
        if contentOffset.y != 0 || contentOffset.x > contentSize.width - bounds.width {
            contentOffset = CGPoint(x: max(0, min(contentOffset.x, contentSize.width - bounds.width)), y: 0)
        }
        let attributed = label.attributedText ?? NSAttributedString()
        layoutBlocks(in: attributed)
        let lineHeight = min(bounds.height - 8, ceil((attributed.length > 0 ? (attributed.attribute(.font, at: 0, effectiveRange: nil) as? UIFont) : nil)?.lineHeight ?? font.lineHeight) + 2)
        if role == .input, let caretLocation {
            caret.isHidden = false
            caret.frame = CGRect(x: x(at: caretLocation, in: attributed) - 1, y: (bounds.height - lineHeight) / 2, width: 2, height: lineHeight)
        } else { caret.isHidden = true }
        guard let focus else { return }
        let signature = "\(attributed.string)\u{0}\(focus.location)\u{0}\(bounds.width)"
        guard signature != followedSignature else { return }
        followedSignature = signature
        let target = x(at: NSMaxRange(focus), in: attributed)
        scrollRectToVisible(CGRect(x: max(0, target - 24), y: 0, width: 48, height: bounds.height), animated: false)
    }
    private func layoutBlocks(in attributed: NSAttributedString) {
        while blockViews.count > blockRanges.count { blockViews.removeLast().removeFromSuperview() }
        while blockViews.count < blockRanges.count {
            let view = UIView(); view.isUserInteractionEnabled = false
            view.layer.cornerRadius = 5; view.layer.cornerCurve = .continuous
            insertSubview(view, belowSubview: label); blockViews.append(view)
        }
        blockFrames = []
        for (index, (range, view)) in zip(blockRanges, blockViews).enumerated() {
            let left = x(at: range.location, in: attributed, beforeGap: false), right = x(at: NSMaxRange(range), in: attributed)
            let frame = CGRect(x: left - 4, y: 4, width: max(8, right - left + 8), height: max(0, bounds.height - 8))
            view.frame = frame; blockFrames.append(frame)
            let active = index == activeBlock
            view.backgroundColor = active ? UIColor.systemTeal.withAlphaComponent(0.12)
                : role == .input ? UIColor.tertiarySystemFill : UIColor.systemBackground.withAlphaComponent(0.6)
            view.layer.borderWidth = active ? 1 : 0
            view.layer.borderColor = UIColor.systemTeal.resolvedColor(with: traitCollection).cgColor
        }
    }
}

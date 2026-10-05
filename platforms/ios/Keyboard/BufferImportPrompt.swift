import UIKit
import UniformTypeIdentifiers

/// System paste control grants access for this explicit tap. The small fallback
/// explains Full Access without inspecting the user's clipboard.
final class BufferPasteButton: UIControl {
    private let native: UIPasteControl
    private let access = UIButton(type: .system)
    var onPaste: (([NSItemProvider]) -> Void)?
    var onNeedsAccess: (() -> Void)?
    var fullAccess = false { didSet { native.isHidden = !fullAccess; access.isHidden = fullAccess } }
    override var isEnabled: Bool { didSet { isUserInteractionEnabled = isEnabled; alpha = isEnabled ? 1 : 0.45 } }

    override init(frame: CGRect) {
        let configuration = UIPasteControl.Configuration()
        configuration.displayMode = .iconOnly
        configuration.baseBackgroundColor = .systemBackground
        configuration.baseForegroundColor = .label
        configuration.cornerStyle = .fixed
        configuration.cornerRadius = 4
        native = UIPasteControl(configuration: configuration)
        super.init(frame: frame)
        accessibilityIdentifier = "keyboard.buffer.paste"
        pasteConfiguration = UIPasteConfiguration(acceptableTypeIdentifiers: [UTType.text.identifier])
        native.target = self; native.isHidden = true
        native.accessibilityLabel = L("粘贴剪贴板文字", "Paste clipboard text")
        native.accessibilityHint = L("插入 Buffer 当前光标位置", "Insert at the Buffer cursor")
        access.setImage(UIImage(systemName: "doc.on.clipboard"), for: .normal)
        access.setPreferredSymbolConfiguration(UIImage.SymbolConfiguration(pointSize: 13, weight: .regular), forImageIn: .normal)
        access.accessibilityLabel = native.accessibilityLabel
        access.addAction(UIAction { [weak self] _ in self?.onNeedsAccess?() }, for: .touchUpInside)
        addSubview(native); addSubview(access)
    }
    required init?(coder: NSCoder) { fatalError() }
    override func canPaste(_ itemProviders: [NSItemProvider]) -> Bool {
        fullAccess && isEnabled && itemProviders.contains { $0.canLoadObject(ofClass: NSString.self) }
    }
    override func paste(itemProviders: [NSItemProvider]) {
        guard fullAccess, isEnabled else { return }
        onPaste?(itemProviders)
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0, bounds.height > 0 else { return }
        // Below its intrinsic size UIPasteControl can render an empty, inert
        // slot. Keep its native layout size and scale it for the shorter row.
        let intrinsic = native.intrinsicContentSize
        let size = CGSize(width: max(44, ceil(intrinsic.width)), height: max(36, ceil(intrinsic.height)))
        let scale = min(1, bounds.width / size.width, bounds.height / size.height)
        native.bounds = CGRect(origin: .zero, size: size)
        native.center = CGPoint(x: bounds.midX, y: bounds.midY)
        native.transform = CGAffineTransform(scaleX: scale, y: scale)
        access.frame = bounds
    }
}

/// A hint inside the empty source line. Its preview never becomes Buffer text.
final class BufferImportPrompt: UIControl {
    private let icon = UIImageView(image: UIImage(systemName: "arrow.down.doc"))
    private let preview = UILabel(), hint = UILabel()
    private var shownText: String?

    override init(frame: CGRect) {
        super.init(frame: frame)
        accessibilityIdentifier = "keyboard.buffer.importHost"
        accessibilityTraits = .button
        isAccessibilityElement = true
        icon.contentMode = .scaleAspectFit
        preview.font = .systemFont(ofSize: 14)
        preview.lineBreakMode = .byTruncatingTail
        hint.font = .systemFont(ofSize: 10, weight: .medium)
        hint.text = L("点此移入", "Tap to import")
        for item in [icon, preview, hint] { item.isUserInteractionEnabled = false; addSubview(item) }
        layer.cornerRadius = 5
        tintColorDidChange()
    }
    required init?(coder: NSCoder) { fatalError() }

    func update(text: String?) {
        guard shownText != text else { return }
        shownText = text
        layer.removeAnimation(forKey: "importHint")
        isHidden = text == nil
        guard let text else { preview.text = nil; accessibilityLabel = nil; return }
        // Only this preview is shortened; the captured source is kept verbatim.
        preview.text = text.replacingOccurrences(of: "\n", with: " ↵ ").replacingOccurrences(of: "\r", with: "")
        accessibilityLabel = L("移入输入框文字：", "Import text from the input field: ") + text
        accessibilityHint = L("轻点导入 Buffer；无法完整读取时保留原文", "Tap to import into Buffer; the original stays if it cannot be read in full")
        guard !UIAccessibility.isReduceMotionEnabled else { return }
        let pulse = CABasicAnimation(keyPath: "opacity")
        pulse.fromValue = 1; pulse.toValue = 0.45; pulse.duration = 0.65
        pulse.autoreverses = true; pulse.repeatCount = 2
        pulse.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        layer.add(pulse, forKey: "importHint")
    }
    override var isHighlighted: Bool { didSet { backgroundColor = isHighlighted ? tintColor.withAlphaComponent(0.12) : .clear } }
    override func tintColorDidChange() {
        super.tintColorDidChange()
        icon.tintColor = tintColor; preview.textColor = .secondaryLabel; hint.textColor = tintColor
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        icon.frame = CGRect(x: 6, y: (bounds.height - 17) / 2, width: 17, height: 17)
        let hintWidth = min(66, ceil(hint.sizeThatFits(bounds.size).width))
        hint.frame = CGRect(x: max(28, bounds.width - hintWidth - 4), y: 0, width: hintWidth, height: bounds.height)
        preview.frame = CGRect(x: 29, y: 0, width: max(0, hint.frame.minX - 35), height: bounds.height)
    }
}

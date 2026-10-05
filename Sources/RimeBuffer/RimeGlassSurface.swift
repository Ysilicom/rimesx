import Cocoa

/// Material treatment adapted from Kindred5210's PR #46. The material stays
/// behind the existing controls: switching themes never reparents input views
/// or changes their constraints, accessibility, or hit testing.
final class RimeGlassBackgroundView: NSView {
    private let fallback = NSVisualEffectView()
    private var nativeGlass: NSView?
    private var observations: [(NotificationCenter, NSObjectProtocol)] = []
    var cornerRadius: CGFloat = 0 { didSet { applyTheme() } }

    override init(frame: NSRect) {
        super.init(frame: frame)
        wantsLayer = true
        fallback.material = .popover
        fallback.blendingMode = .behindWindow
        fallback.state = .active
        fallback.frame = bounds
        fallback.autoresizingMask = [.width, .height]
        addSubview(fallback)
        if #available(macOS 26.0, *) {
            let glass = NSGlassEffectView(frame: bounds)
            glass.style = .regular
            glass.autoresizingMask = [.width, .height]
            // A background sibling avoids NSGlassEffectView's content sizing
            // rules changing the candidate strip or settings page geometry.
            glass.contentView = NSView(frame: bounds)
            addSubview(glass)
            nativeGlass = glass
        }
        for (center, name) in [
            (NotificationCenter.default, Notification.Name.rimeAppearanceDidChange),
            (NSWorkspace.shared.notificationCenter,
             NSWorkspace.accessibilityDisplayOptionsDidChangeNotification),
        ] {
            let token = center.addObserver(forName: name, object: nil, queue: .main) {
                [weak self] _ in self?.applyTheme()
            }
            observations.append((center, token))
        }
        applyTheme()
    }

    required init?(coder: NSCoder) { nil }

    deinit {
        for (center, token) in observations { center.removeObserver(token) }
    }

    // Only the decorative backing ignores events; controls remain siblings.
    override func hitTest(_ point: NSPoint) -> NSView? { nil }

    override func viewDidChangeEffectiveAppearance() {
        super.viewDidChangeEffectiveAppearance()
        applyTheme()
    }

    var hasVisibleMaterial: Bool {
        !isHidden && (nativeGlass?.isHidden == false || !fallback.isHidden)
    }

    func applyTheme() {
        isHidden = !RimeUI.usesLiquidGlassTransparency
        layer?.cornerRadius = cornerRadius
        layer?.cornerCurve = .continuous
        layer?.masksToBounds = true
        if #available(macOS 26.0, *), let glass = nativeGlass as? NSGlassEffectView {
            glass.cornerRadius = cornerRadius
            glass.tintColor = nil
            glass.isHidden = isHidden
            fallback.isHidden = true
        } else {
            RoundedWindowChrome.maskMaterial(fallback, radius: cornerRadius)
            fallback.isHidden = isHidden
        }
    }
}

final class RimeCandidateSurfaceView: NSView {
    private let glass = RimeGlassBackgroundView()
    private let isPreedit: Bool
    private var observations: [(NotificationCenter, NSObjectProtocol)] = []

    init(isPreedit: Bool) {
        self.isPreedit = isPreedit
        super.init(frame: .zero)
        wantsLayer = true
        glass.frame = bounds
        glass.autoresizingMask = [.width, .height]
        addSubview(glass)
        for (center, name) in [
            (NotificationCenter.default, Notification.Name.rimeAppearanceDidChange),
            (NSWorkspace.shared.notificationCenter,
             NSWorkspace.accessibilityDisplayOptionsDidChangeNotification),
        ] {
            observations.append((center, center.addObserver(
                forName: name, object: nil, queue: .main
            ) { [weak self] _ in self?.applyTheme() }))
        }
        applyTheme()
    }

    required init?(coder: NSCoder) { nil }

    deinit {
        for (center, token) in observations { center.removeObserver(token) }
    }

    override func viewDidChangeEffectiveAppearance() {
        super.viewDidChangeEffectiveAppearance()
        applyTheme()
    }

    var hasVisibleMaterial: Bool { glass.hasVisibleMaterial }

    func applyTheme() {
        let transparent = RimeUI.usesLiquidGlassTransparency
        let radius = isPreedit ? CandidateLayout.preeditCornerRadius : CandidateLayout.stripCornerRadius
        glass.cornerRadius = radius
        layer?.cornerRadius = radius
        layer?.cornerCurve = .continuous
        layer?.masksToBounds = true
        layer?.backgroundColor = transparent ? NSColor.clear.cgColor
            : RimeUI.candidateBackgroundColor.withAlphaComponent(isPreedit && !RimeUI.isLiquidGlass ? 0.95 : 1).cgColor
        layer?.borderWidth = transparent ? 0.6 : 1
        layer?.borderColor = transparent ? NSColor.white.withAlphaComponent(0.24).cgColor
            : RimeUI.borderStrong.withAlphaComponent(isPreedit ? 0.72 : 1).cgColor
    }
}

/// Refresh rasterized labels and settings colors as well as native materials
/// when the system appearance/accessibility options change while Glass is open.
final class RimeSystemAppearanceObservation {
    private var appearance: NSKeyValueObservation?
    private var accessibility: NSObjectProtocol?

    init() {
        appearance = NSApplication.shared.observe(\.effectiveAppearance, options: [.new]) { _, _ in
            Self.refresh()
        }
        accessibility = NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.accessibilityDisplayOptionsDidChangeNotification,
            object: nil, queue: .main
        ) { _ in Self.refresh() }
    }

    private static func refresh() {
        DispatchQueue.main.async {
            guard RimeUI.isLiquidGlass else { return }
            NotificationCenter.default.post(name: .rimeAppearanceDidChange, object: nil)
        }
    }

    deinit {
        if let accessibility {
            NSWorkspace.shared.notificationCenter.removeObserver(accessibility)
        }
    }
}

import UIKit
import RimesCore

// Enable Apple's click API on the original UIKit input view. Keeping that view
// preserves the host's self-sizing and Buffer expansion behavior.
extension UIInputView: @retroactive UIInputViewAudioFeedback {
    public var enableInputClicksWhenVisible: Bool { true }
}

@MainActor final class KeyboardFeedback {
    var enabled = true
    var soundEnabled = true
    var playInputClick: () -> Void = { UIDevice.current.playInputClick() }
    var strength: HapticStrength = .light
    private let strong = UIImpactFeedbackGenerator(style: .medium)
    private let strongest = UIImpactFeedbackGenerator(style: .heavy)
    private let press = UIImpactFeedbackGenerator(style: .light)
    private let selection = UISelectionFeedbackGenerator()
    private let commit = UIImpactFeedbackGenerator(style: .medium)
    private var gate = FeedbackGate()
    private var soundGate = FeedbackGate()
    var clock: () -> TimeInterval = { ProcessInfo.processInfo.systemUptime }
    #if DEBUG
    var onFeedback: ((KeyFeedback) -> Void)?
    var onFeedbackStrength: ((HapticStrength) -> Void)?
    #endif
    func reset() { gate.reset() }
    func prepare() {
        guard enabled else { return }
        switch strength {
        case .light: press.prepare()
        case .strong: strong.prepare()
        case .strongest: strongest.prepare()
        }
    }
    func send(_ event: KeyFeedback, combination: String? = nil, minimumStrength: HapticStrength = .light) {
        let now = clock()
        // Press only: chord previews and commits must not add extra clicks.
        // Haptics go first; audio stays independent of the user's haptic setting.
        defer { if soundEnabled, event == .press, soundGate.accept(event, at: now) { playInputClick() } }
        guard enabled, gate.accept(event, combination: combination, at: now) else { return }
        let appliedStrength: HapticStrength = strength == .strongest || minimumStrength == .strongest ? .strongest
            : strength == .strong || minimumStrength == .strong ? .strong : .light
        #if DEBUG
        onFeedback?(event)
        onFeedbackStrength?(appliedStrength)
        #endif
        if appliedStrength != .light {
            let generator = appliedStrength == .strong ? strong : strongest
            generator.impactOccurred(intensity: appliedStrength == .strong ? 0.85 : 1)
            prepare(); generator.prepare()
            return
        }
        switch event {
        case .press: press.impactOccurred()
        case .selection: selection.selectionChanged(); selection.prepare()
        case .commit: commit.impactOccurred(intensity: 0.65)
        }
        // Preparing immediately before impact has no latency benefit. Keep the
        // engine ready after this pulse for the next touch instead.
        prepare()
    }
}

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
    #endif
    func reset() { gate.reset() }
    func send(_ event: KeyFeedback, combination: String? = nil) {
        let now = clock()
        // Press only: chord previews and commits must not add extra clicks.
        // Audio stays independent of the user's haptic setting.
        if soundEnabled, event == .press, soundGate.accept(event, at: now) { playInputClick() }
        guard enabled, gate.accept(event, combination: combination, at: now) else { return }
        #if DEBUG
        onFeedback?(event)
        #endif
        if strength != .light {
            let generator = strength == .strong ? strong : strongest
            generator.prepare(); generator.impactOccurred(intensity: strength == .strong ? 0.85 : 1)
            return
        }
        switch event {
        case .press: press.prepare(); press.impactOccurred(intensity: 0.55)
        case .selection: selection.prepare(); selection.selectionChanged()
        case .commit: commit.prepare(); commit.impactOccurred(intensity: 0.65)
        }
    }
}

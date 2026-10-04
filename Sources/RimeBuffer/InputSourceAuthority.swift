import Carbon.HIToolbox
import Foundation

/// Live authority for callbacks entering this process through InputMethodKit.
///
/// ETInput stays alive for its global utility-window shortcuts even while a
/// different input source is selected. macOS can still deliver late lifecycle
/// or key callbacks to the old IMK connection during that handoff. Those
/// callbacks must pass through without rebuilding a Rime focus lease or
/// touching the old client.
enum RimeInputSourceAuthority {
    static func isOwnInputSourceID(
        _ inputSourceID: String,
        ownBundleID: String = Bundle.main.bundleIdentifier
            ?? RimesIdentity.bundleIdentifier
    ) -> Bool {
        inputSourceID == ownBundleID
            || inputSourceID.hasPrefix(ownBundleID + ".")
    }

    static func currentInputSourceID() -> String? {
        guard let source = TISCopyCurrentKeyboardInputSource()?
            .takeRetainedValue(),
              let pointer = TISGetInputSourceProperty(
                source,
                kTISPropertyInputSourceID
              ) else {
            return nil
        }
        return Unmanaged<CFString>.fromOpaque(pointer)
            .takeUnretainedValue() as String
    }

    /// Query TIS at the callback boundary instead of trusting a notification
    /// cache. The distributed source-change notification can arrive after a
    /// stale activate/key callback, which is precisely the race this gate
    /// closes.
    static func currentSourceIsOwn() -> Bool {
        guard let inputSourceID = currentInputSourceID() else { return false }
        return isOwnInputSourceID(inputSourceID)
    }
}

enum RimeInputSourceSelectionRules {
    /// A component opens once RIMES owns the input source and, when it works
    /// on the focused field, RIMES has taken that field over. The timeout
    /// covers a front app with nothing editable focused and a refused switch.
    static func mayOpen(sourceIsOwn: Bool,
                        hasFocusTarget: Bool,
                        awaitsFocus: Bool,
                        timedOut: Bool) -> Bool {
        timedOut || (sourceIsOwn && (hasFocusTarget || !awaitsFocus))
    }
}

/// Calling up a RIMES component while another input method is selected
/// selects RIMES first: Buffer capture, the Capsule rail's direct insertion,
/// Settings and the rest only work fully while RIMES owns the keyboard. The
/// switch happens once, when the user asks for the component; switching away
/// later is the user's choice and is never undone.
enum RimeInputSourceSelection {
    static let handoffTimeout: TimeInterval = 0.35
    private static let pollInterval: TimeInterval = 0.02

    /// Runs `open` right away under RIMES. Otherwise selects RIMES and runs it
    /// once `RimeInputSourceSelectionRules.mayOpen` allows, so the component
    /// sees RIMES authority instead of settling into its reduced mode.
    static func open(_ component: String,
                     awaitsFocus: Bool,
                     _ open: @escaping () -> Void) {
        dispatchPrecondition(condition: .onQueue(.main))
        guard !RimeInputSourceAuthority.currentSourceIsOwn() else {
            open()
            return
        }
        guard selectRIMES() else {
            IMELog.write("RIMES input source unavailable; opening \(component) as is")
            open()
            return
        }
        let deadline = ProcessInfo.processInfo.systemUptime + handoffTimeout
        func poll() {
            let sourceIsOwn = RimeInputSourceAuthority.currentSourceIsOwn()
            let timedOut = ProcessInfo.processInfo.systemUptime >= deadline
            guard RimeInputSourceSelectionRules.mayOpen(
                sourceIsOwn: sourceIsOwn,
                hasFocusTarget: InputFocusCoordinator.shared.liveTarget() != nil,
                awaitsFocus: awaitsFocus,
                timedOut: timedOut
            ) else {
                DispatchQueue.main.asyncAfter(deadline: .now() + pollInterval) { poll() }
                return
            }
            IMELog.write("RIMES selected to open \(component) own=\(sourceIsOwn) timedOut=\(timedOut)")
            open()
        }
        poll()
    }

    /// Selects the enabled RIMES keyboard mode. Some macOS builds report an
    /// error while still applying the change, so the caller watches the
    /// current source instead of trusting the status.
    private static func selectRIMES() -> Bool {
        let ownBundleID = Bundle.main.bundleIdentifier ?? RimesIdentity.bundleIdentifier
        let filter = [kTISPropertyBundleID as String: ownBundleID] as CFDictionary
        guard let sources = TISCreateInputSourceList(filter, false)?
                .takeRetainedValue() as? [TISInputSource],
              let mode = sources.first(where: { source in
                  guard let pointer = TISGetInputSourceProperty(
                    source,
                    kTISPropertyInputSourceIsSelectCapable
                  ) else { return false }
                  return Unmanaged<NSNumber>.fromOpaque(pointer)
                      .takeUnretainedValue().boolValue
              }) else {
            return false
        }
        let status = TISSelectInputSource(mode)
        IMELog.write("RIMES input source select status=\(status)")
        return true
    }
}

import AppKit

/// Morse keying, decoding, delivery and sound on a synthetic clock. Never
/// opens an audio device or touches live Buffer state.
@MainActor
func runMorseCodeSmokeTest(output: URL? = nil) -> Bool {
    func fail(_ message: String) -> Bool {
        print("FAILED: morse \(message)")
        return false
    }

    // Alphabet.
    let letters = MorseAlphabet.table.filter { $0.value.isLetter }
    let digits = MorseAlphabet.table.filter { $0.value.isNumber }
    guard letters.count == 26, digits.count == 10,
          Set(MorseAlphabet.table.values).count == MorseAlphabet.table.count,
          MorseAlphabet.character(for: [.dot]) == "E",
          MorseAlphabet.character(for: [.dash]) == "T",
          MorseAlphabet.character(for: [.dot, .dot, .dot]) == "S",
          MorseAlphabet.hasContinuation([.dot, .dash]),
          !MorseAlphabet.hasContinuation([.dash, .dash, .dot, .dash, .dash]),
          MorseAlphabet.isErrorSign(Array(repeating: .dot, count: 8)) else {
        return fail("alphabet covers A–Z, 0–9 and punctuation uniquely")
    }

    // Keyer on a synthetic clock (standard: dash ≥ 200 ms, letter 480 ms, word 1.25 s).
    var keyer = MorseKeyer(timing: .standard)
    guard keyer.press(at: 0), !keyer.press(at: 0.05),
          keyer.release(at: 0.08) == .dot,
          keyer.advance(to: 0.4).isEmpty,
          keyer.press(at: 0.4), keyer.release(at: 0.7) == .dash,
          keyer.nextDeadline == 0.7 + 0.48,
          keyer.advance(to: 1.2) == [.letter("A", [.dot, .dash])],
          keyer.advance(to: 1.5).isEmpty,
          keyer.advance(to: 2.0) == [.wordBreak],
          keyer.nextDeadline == nil else {
        return fail("presses become dots and dashes; pauses end letters and words")
    }

    // Workspace.
    let suite = "RimeBuffer.MorseSmoke.\(UUID().uuidString)"
    guard let defaults = UserDefaults(suiteName: suite) else { return fail("defaults") }
    defer { defaults.removePersistentDomain(forName: suite) }
    let center = NotificationCenter()
    let sound = MorseSoundRecorder()
    var now: TimeInterval = 100
    var selected = true
    let workspace = MorseWorkspace(defaults: defaults, notificationCenter: center, sound: sound,
                                   clock: { now }, isSelected: { selected })
    workspace.start()
    defer { workspace.stop() }

    func key(_ code: UInt16, down: Bool, repeatKey: Bool = false, characters: String? = " ",
             modifiers: NSEvent.ModifierFlags = []) -> Bool {
        if !down { return workspace.consumeOwnedKeyUp(code: code, timestamp: now) }
        return workspace.handleKey(code: code, isDown: true, isRepeat: repeatKey,
                                   modifiers: modifiers, characters: characters, timestamp: now)
    }
    /// Keys one letter: "." short, "-" long, then pauses for the letter.
    func tap(_ code: String) {
        for symbol in code {
            _ = key(MorseWorkspace.spaceKeyCode, down: true)
            now += symbol == "." ? 0.09 : 0.3
            _ = key(MorseWorkspace.spaceKeyCode, down: false)
            now += 0.1
        }
        now += 0.5
        workspace.advance(to: now)
    }

    guard key(MorseWorkspace.spaceKeyCode, down: true),
          key(MorseWorkspace.spaceKeyCode, down: true, repeatKey: true),
          sound.keyDowns == 1 else {
        return fail("Space is owned and key repeat is not a second press")
    }
    now += 0.09
    guard key(MorseWorkspace.spaceKeyCode, down: false), sound.keyUps >= 1,
          workspace.tapeSnapshot().pending == [.dot],
          workspace.tapeSnapshot().candidate == "E" else {
        return fail("a short press is a dot and the mapping slot shows its letter")
    }
    now += 0.6
    workspace.advance(to: now)
    tap("...")
    tap("---")
    tap("...")
    guard workspace.outputText == "ESOS", sound.letters == 4 else {
        return fail("letters decode with a chime each: \(workspace.outputText)")
    }
    guard workspace.deliveryPendingBlocks.isEmpty, workspace.hasIncompleteDeliveryBlocks else {
        return fail("the word being keyed is not yet deliverable")
    }
    now += 1.5
    workspace.advance(to: now)
    guard workspace.deliveryPendingBlocks.map(\.text) == ["ESOS"] else {
        return fail("a long pause ends the word and makes it deliverable")
    }
    tap("....")
    tap("..")
    guard workspace.outputText == "ESOS HI" else { return fail("the next word gets its space") }

    // Delete: a pending symbol first, then the last letter.
    _ = key(MorseWorkspace.spaceKeyCode, down: true); now += 0.3
    _ = key(MorseWorkspace.spaceKeyCode, down: false)
    guard workspace.tapeSnapshot().pending == [.dash],
          key(MorseWorkspace.deleteKeyCode, down: true, characters: "\u{7f}"),
          workspace.tapeSnapshot().pending.isEmpty,
          key(MorseWorkspace.deleteKeyCode, down: true, characters: "\u{7f}"),
          workspace.outputText == "ESOS H" else {
        return fail("Delete removes a pending symbol, then a letter")
    }
    tap("..")
    // Unknown code and the error sign.
    let rejectsBefore = sound.rejects
    tap("--.--")
    guard workspace.outputText == "ESOS HI", sound.rejects == rejectsBefore + 1,
          workspace.tapeSnapshot().lastRejected else {
        return fail("a code that means nothing is rejected with a low blip")
    }
    tap("........")
    guard workspace.outputText == "ESOS H" else { return fail("eight dots erase a character") }
    tap("..")

    // Other typing never leaks into the hidden source rail.
    guard key(0, down: true, characters: "a"), key(0, down: false),
          !key(36, down: true, characters: "\r"),
          !key(8, down: true, characters: "c", modifiers: .command),
          !key(123, down: true, characters: "\u{F702}") else {
        return fail("letters are swallowed; Return, shortcuts and arrows stay Buffer's")
    }

    // Return: the open word is committed and everything becomes deliverable.
    tap("-")
    guard workspace.prepareForDelivery(),
          workspace.deliveryPendingBlocks.map(\.text) == ["ESOS", " HIT"],
          !workspace.hasIncompleteDeliveryBlocks else {
        return fail("Return commits the open word: \(workspace.deliveryPendingBlocks.map(\.text))")
    }
    let blocks = workspace.deliveryPendingBlocks
    let generation = workspace.deliveryGeneration
    guard workspace.deliveryBlock(id: blocks[0].id, generation: generation)?.text == "ESOS",
          workspace.consumeDeliveredAndReportTerminalDrain(blockIDs: [blocks[0].id],
                                                           generation: generation) == nil,
          workspace.outputText == " HIT",
          workspace.deliveryGeneration != generation else {
        return fail("sending a word removes only that word")
    }
    let last = workspace.deliveryPendingBlocks
    guard workspace.consumeDeliveredAndReportTerminalDrain(
            blockIDs: last.map(\.id), generation: workspace.deliveryGeneration
          ) != nil,
          workspace.outputText.isEmpty,
          workspace.railSnapshot.outputBlocks.isEmpty else {
        return fail("sending the last word reports a terminal drain")
    }

    // Speed picker persists; protection and deselection clear state.
    guard workspace.optionPickerOptions.map(\.identifier) == ["slow", "standard", "fast"],
          workspace.setOptionPickerSelection("slow"),
          defaults.string(forKey: MorseWorkspace.speedKey) == "slow",
          workspace.timing == .slow else {
        return fail("speed choice is stored")
    }
    tap("..")
    _ = key(MorseWorkspace.spaceKeyCode, down: true)
    workspace.setProtected(true)
    guard workspace.outputText.isEmpty, !workspace.tapeSnapshot().isPressed,
          !workspace.handleKey(code: MorseWorkspace.spaceKeyCode, isDown: true, isRepeat: false,
                               modifiers: [], characters: " ", timestamp: now),
          sound.shutdowns >= 1 else {
        return fail("protection discards keyed text, silences the tone and releases Space")
    }
    workspace.setProtected(false)
    selected = false
    guard !workspace.handleKey(code: MorseWorkspace.spaceKeyCode, isDown: true, isRepeat: false,
                               modifiers: [], characters: " ", timestamp: now) else {
        return fail("an unselected Morse never takes Space")
    }
    selected = true

    // Sound: tone while keyed, silence after the release ramp, a blip on top.
    var kernel = MorseSynthKernel(sampleRate: 48_000)
    var samples = [Float](repeating: 0, count: 4_800)
    func rms() -> Float { sqrt(samples.reduce(0) { $0 + $1 * $1 } / Float(samples.count)) }
    kernel.keyed = true
    samples.withUnsafeMutableBufferPointer { kernel.render(into: $0.baseAddress!, count: $0.count) }
    let keyedLevel = rms()
    let peak = samples.map(abs).max() ?? 0
    kernel.keyed = false
    samples.withUnsafeMutableBufferPointer { kernel.render(into: $0.baseAddress!, count: $0.count) }
    let tail = samples.suffix(2_000).map(abs).max() ?? 1
    kernel.blip(frequency: 1_320, seconds: 0.035)
    samples.withUnsafeMutableBufferPointer { kernel.render(into: $0.baseAddress!, count: $0.count) }
    let blipLevel = rms()
    guard keyedLevel > 0.1, peak <= MorseSynthKernel.toneLevel + 0.001,
          tail == 0, blipLevel > 0.005,
          abs(samples.first ?? 1) < 0.01 else {
        return fail("sidetone \(keyedLevel), release tail \(tail), blip \(blipLevel)")
    }

    // Input bar slots.
    let bar = MorseInputBarView(workspace: workspace)
    bar.frame = NSRect(x: 0, y: 0, width: 640, height: BufferInlineMetrics.itemHeight)
    var snapshot = MorseTapeSnapshot()
    snapshot.now = 12.3
    snapshot.pending = [.dot, .dash]
    snapshot.candidate = "A"
    snapshot.isPressed = true
    snapshot.pressIsDash = true
    snapshot.marks = [
        .init(start: 10.0, end: 10.09, symbol: .dot),
        .init(start: 10.3, end: 10.6, symbol: .dash),
        .init(start: 11.8, end: 12.3, symbol: nil),
    ]
    snapshot.letterTicks = [11.1]
    bar.render(snapshot)
    guard bar.renderedMappingTextForSmoke == "A",
          bar.accessibilityValue() as? String == "待组合 点 划，对应 A，按键按下" else {
        return fail("the mapping slot shows the letter and the bar describes itself")
    }
    if let output {
        bar.appearance = NSAppearance(named: .darkAqua)
        let container = NSView(frame: NSRect(x: 0, y: 0, width: 660, height: 40))
        container.wantsLayer = true
        container.layer?.backgroundColor = RimeUI.candidateBackgroundColor.cgColor
        bar.frame.origin = NSPoint(x: 10, y: 10)
        container.addSubview(bar)
        if let bitmap = container.bitmapImageRepForCachingDisplay(in: container.bounds) {
            container.cacheDisplay(in: container.bounds, to: bitmap)
            try? bitmap.representation(using: .png, properties: [:])?.write(to: output)
        }
    }
    snapshot.pending = [.dash, .dash, .dot, .dash, .dash]
    snapshot.candidate = nil
    snapshot.pendingIsDeadEnd = true
    bar.render(snapshot)
    guard bar.renderedMappingTextForSmoke == "?" else {
        return fail("a sequence that can form nothing shows ?")
    }

    // The bar in the real Buffer rail: it fills the upper row and the decoded
    // words sit below it.
    let rail = BufferInlineView(frame: NSRect(
        x: 0, y: 0, width: 760, height: BufferInlineView.translationPreferredHeight(targetRows: 1)
    ))
    let railBar = MorseInputBarView(workspace: workspace)
    rail.sourceReplacementView = railBar
    railBar.render(snapshot)
    let railSnapshot = TranslationRailSnapshot(
        sourceText: "",
        sourceRailPinned: true,
        outputBlocks: [
            TranslationOutputBlock(id: UUID(), text: "SOS", deliveryReady: true),
            TranslationOutputBlock(id: UUID(), text: " HI", deliveryReady: false),
        ],
        phase: .ready, sourceRole: "码", targetRole: "文",
        sourceEmptyText: "", targetEmptyText: "等待电码"
    )
    guard rail.renderTranslationForPreview(railSnapshot) else { return fail("rail renders") }
    rail.layoutSubtreeIfNeeded()
    let barFrame = railBar.convert(railBar.bounds, to: rail)
    guard railBar.superview != nil, barFrame.width > 600,
          rail.renderedTextFragments.contains("SOS"),
          !rail.renderedInputCaretVisible else {
        return fail("the bar fills the upper rail (\(barFrame.width)) above the words")
    }
    if let output {
        let railURL = output.deletingPathExtension().appendingPathExtension("rail.png")
        rail.appearance = NSAppearance(named: .darkAqua)
        if let bitmap = rail.bitmapImageRepForCachingDisplay(in: rail.bounds) {
            rail.cacheDisplay(in: rail.bounds, to: bitmap)
            try? bitmap.representation(using: .png, properties: [:])?.write(to: railURL)
        }
    }

    print("PASS: morse alphabet, keyer timing, words and delivery, delete and error sign, sound, input bar, rail")
    return true
}

private final class MorseSoundRecorder: MorseSoundOutput {
    var keyDowns = 0
    var keyUps = 0
    var letters = 0
    var rejects = 0
    var shutdowns = 0
    func keyDown() { keyDowns += 1 }
    func keyUp() { keyUps += 1 }
    func letterBlip() { letters += 1 }
    func rejectBlip() { rejects += 1 }
    func shutdown() { shutdowns += 1 }
}

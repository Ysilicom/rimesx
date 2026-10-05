import XCTest
import UIKit
@testable import RIMES

@MainActor final class ProxyTextDeliveryTests: XCTestCase {
    func testCommitReplacesCompositionInSelectionOnlyHost() async throws {
        let (window, controller, delivery) = host()
        defer { window.isHidden = true }
        let proxy = controller.proxy
        proxy.insertionReplacesSelectionOnly = true
        proxy.native.text = "前😀后"
        proxy.native.selectedRange = NSRange(location: 3, length: 0)
        XCTAssertTrue(delivery.updateMarkedText("ni'hao", target: proxy.documentIdentifier))

        XCTAssertTrue(delivery.insert("你好", target: proxy.documentIdentifier))
        await drainRemoteEdits()
        XCTAssertEqual(proxy.native.text, "前😀你好后")
        XCTAssertNil(proxy.native.markedTextRange)
        XCTAssertEqual(proxy.native.selectedRange, NSRange(location: 5, length: 0))

        // Same-key top-up must start a fresh composition after the committed word.
        XCTAssertTrue(delivery.updateMarkedText("ma", target: proxy.documentIdentifier))
        XCTAssertEqual(proxy.native.text, "前😀你好ma后")
        XCTAssertTrue(delivery.insert("吗", target: proxy.documentIdentifier))
        await drainRemoteEdits()
        XCTAssertEqual(proxy.native.text, "前😀你好吗后")
        XCTAssertEqual(proxy.native.selectedRange, NSRange(location: 6, length: 0))
        XCTAssertFalse(delivery.hasMarkedText)
    }

    func testCandidateSurvivesHostReconciliationAfterCompositionEnds() async {
        let (window, controller, delivery) = host()
        defer { window.isHidden = true }
        let proxy = controller.proxy
        proxy.reconcileEmptyComposition = true
        proxy.native.text = "前😀后"
        proxy.native.selectedRange = NSRange(location: 3, length: 0)

        XCTAssertTrue(delivery.updateMarkedText("ni hao", target: proxy.documentIdentifier))
        XCTAssertTrue(delivery.insert("你好", target: proxy.documentIdentifier))
        proxy.flushHostReconciliation()
        await drainRemoteEdits()
        XCTAssertEqual(proxy.native.text, "前😀你好后")
        XCTAssertNil(proxy.native.markedTextRange)
        XCTAssertEqual(proxy.native.selectedRange, NSRange(location: 5, length: 0))

        XCTAssertTrue(delivery.updateMarkedText("ma", target: proxy.documentIdentifier))
        XCTAssertTrue(delivery.insert("吗", target: proxy.documentIdentifier))
        proxy.flushHostReconciliation()
        await drainRemoteEdits()
        XCTAssertEqual(proxy.native.text, "前😀你好吗后")
        XCTAssertEqual(proxy.native.selectedRange, NSRange(location: 6, length: 0))
        XCTAssertFalse(delivery.hasMarkedText)
    }

    func testCommitSurvivesImmediateContinuationWhenRemoteUnmarkIsDeferred() async throws {
        let (window, controller, delivery) = host()
        defer { window.isHidden = true }
        let proxy = controller.proxy
        proxy.native.text = "前😀后"
        proxy.native.selectedRange = NSRange(location: 3, length: 0)
        XCTAssertTrue(delivery.updateMarkedText("nkhz", target: proxy.documentIdentifier))

        XCTAssertTrue(delivery.insert("你好", target: proxy.documentIdentifier))
        XCTAssertTrue(delivery.updateMarkedText("n", target: proxy.documentIdentifier))
        await drainRemoteEdits()

        XCTAssertEqual(proxy.native.text, "前😀你好n后")
        let range = try XCTUnwrap(proxy.native.markedTextRange)
        XCTAssertEqual(proxy.native.text(in: range), "n")
        delivery.discardMarkedText()
        XCTAssertEqual(proxy.native.text, "前😀你好后", "Discarding the continuation must never discard the committed word.")
    }

    func testCommitReplacesEntireCompositionWithAnInteriorSelection() async {
        let (window, controller, delivery) = host()
        defer { window.isHidden = true }
        let proxy = controller.proxy
        proxy.native.text = "前😀后"
        proxy.native.selectedRange = NSRange(location: 3, length: 0)
        XCTAssertTrue(delivery.updateMarkedText("nkhz", target: proxy.documentIdentifier))
        // A host can keep an interior selected range inside the active mark.
        proxy.native.selectedRange = NSRange(location: 4, length: 1)

        XCTAssertTrue(delivery.insert("你好", target: proxy.documentIdentifier))
        await drainRemoteEdits()

        XCTAssertEqual(proxy.native.text, "前😀你好后")
        XCTAssertFalse(delivery.hasMarkedText)
    }

    func testQueuedContinuationCommitsAndLiteralEditsKeepTheirOrder() async {
        let (window, controller, delivery) = host()
        defer { window.isHidden = true }
        let proxy = controller.proxy, target = controller.proxy.documentIdentifier
        XCTAssertTrue(delivery.updateMarkedText("nihao", target: target))
        XCTAssertTrue(delivery.insert("你好", target: target))
        XCTAssertTrue(delivery.updateMarkedText("ma", target: target))
        XCTAssertTrue(delivery.insert("吗", target: target))
        XCTAssertTrue(delivery.insert("!", target: target))
        XCTAssertTrue(delivery.deleteBackward(target: target))
        XCTAssertTrue(delivery.insert("？", target: target))
        await drainRemoteEdits()
        XCTAssertEqual(proxy.native.text, "你好吗？")
        XCTAssertEqual(proxy.native.selectedRange, NSRange(location: 4, length: 0))
        XCTAssertFalse(delivery.hasMarkedText)
    }

    func testQueuedPreeditCannotModifyChangedTargetOrMovedCaret() async {
        for targetChanged in [false, true] {
            let (window, controller, delivery) = host()
            defer { window.isHidden = true }
            let proxy = controller.proxy, target = controller.proxy.documentIdentifier
            XCTAssertTrue(delivery.updateMarkedText("ni", target: target))
            XCTAssertTrue(delivery.insert("你", target: target))
            XCTAssertTrue(delivery.updateMarkedText("hao", target: target))
            if targetChanged {
                proxy.documentIdentifier = UUID(); proxy.native.text = "新输入框"
            } else {
                delivery.abandonMarkedText(); proxy.native.selectedRange = NSRange(location: 0, length: 0)
            }
            await drainRemoteEdits()
            XCTAssertEqual(proxy.native.text, targetChanged ? "新输入框" : "你")
            XCTAssertFalse(delivery.hasMarkedText)
            if !targetChanged { XCTAssertEqual(proxy.native.selectedRange, NSRange(location: 0, length: 0)) }
        }
    }

    private func drainRemoteEdits() async {
        // Each committed composition has its own unmark/continuation boundary.
        for _ in 0..<4 {
            await withCheckedContinuation { continuation in
                DispatchQueue.main.async { continuation.resume() }
            }
        }
    }

    private func host() -> (UIWindow, DeferredUnmarkController, ProxyTextDelivery) {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 900))
        let parent = UIViewController(); window.rootViewController = parent; window.makeKeyAndVisible()
        let controller = DeferredUnmarkController()
        parent.addChild(controller); parent.view.addSubview(controller.view); controller.didMove(toParent: parent)
        return (window, controller, ProxyTextDelivery(controller: controller) { true })
    }
}

@MainActor private final class DeferredUnmarkController: UIInputViewController {
    let proxy = DeferredUnmarkDocumentProxy()
    override var textDocumentProxy: any UITextDocumentProxy { proxy }
}

/// Models a remote text host that has not processed an unmark notification before
/// the keyboard queues its next marked-text update. Actual text edits use UIKit.
@MainActor private final class DeferredUnmarkDocumentProxy: NSObject, UITextDocumentProxy {
    let native = UITextView()
    var documentIdentifier = UUID()
    var reconcileEmptyComposition = false
    var insertionReplacesSelectionOnly = false
    private var pendingHostText: String?
    var documentContextBeforeInput: String? { (native.text as NSString).substring(to: native.selectedRange.location) }
    var documentContextAfterInput: String? { (native.text as NSString).substring(from: NSMaxRange(native.selectedRange)) }
    var selectedText: String? { native.selectedRange.length == 0 ? nil : (native.text as NSString).substring(with: native.selectedRange) }
    var documentInputMode: UITextInputMode? { nil }
    var hasText: Bool { native.hasText }
    func insertText(_ text: String) {
        if insertionReplacesSelectionOnly {
            // Some custom UITextInput hosts insert at the selection, even with
            // a nonempty marked range. Unlike UITextView they do not implicitly
            // replace that entire range. Match that contract using native edits.
            let selection = native.selectedRange
            native.unmarkText(); native.selectedRange = selection
        }
        native.insertText(text)
    }
    func deleteBackward() { native.deleteBackward() }
    func setMarkedText(_ text: String, selectedRange: NSRange) {
        native.setMarkedText(text, selectedRange: selectedRange)
        // A controlled host can reconcile the cancelled composition from its
        // text model after the keyboard has already queued another edit.
        if reconcileEmptyComposition && text.isEmpty { pendingHostText = native.text }
    }
    func flushHostReconciliation() {
        if let pendingHostText { native.text = pendingHostText }
        pendingHostText = nil
    }
    func unmarkText() { DispatchQueue.main.async { self.native.unmarkText() } }
    func adjustTextPosition(byCharacterOffset offset: Int) {
        native.selectedRange = NSRange(location: max(0, min(native.text.utf16.count, native.selectedRange.location + offset)), length: 0)
    }
}

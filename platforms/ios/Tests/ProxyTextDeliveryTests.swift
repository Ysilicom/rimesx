import XCTest
import UIKit
@testable import RIMES

@MainActor final class ProxyTextDeliveryTests: XCTestCase {
    func testCommitSurvivesImmediateContinuationWhenRemoteUnmarkIsDeferred() throws {
        let (window, controller, delivery) = host()
        defer { window.isHidden = true }
        let proxy = controller.proxy
        proxy.native.text = "前😀后"
        proxy.native.selectedRange = NSRange(location: 3, length: 0)
        XCTAssertTrue(delivery.updateMarkedText("nkhz", target: proxy.documentIdentifier))

        XCTAssertTrue(delivery.insert("你好", target: proxy.documentIdentifier))
        XCTAssertTrue(delivery.updateMarkedText("n", target: proxy.documentIdentifier))

        XCTAssertEqual(proxy.native.text, "前😀你好n后")
        let range = try XCTUnwrap(proxy.native.markedTextRange)
        XCTAssertEqual(proxy.native.text(in: range), "n")
        delivery.discardMarkedText()
        XCTAssertEqual(proxy.native.text, "前😀你好后", "Discarding the continuation must never discard the committed word.")
    }

    func testCommitReplacesEntireCompositionWithAnInteriorSelection() {
        let (window, controller, delivery) = host()
        defer { window.isHidden = true }
        let proxy = controller.proxy
        proxy.native.text = "前😀后"
        proxy.native.selectedRange = NSRange(location: 3, length: 0)
        XCTAssertTrue(delivery.updateMarkedText("nkhz", target: proxy.documentIdentifier))
        // A host can keep an interior selected range inside the active mark.
        proxy.native.selectedRange = NSRange(location: 4, length: 1)

        XCTAssertTrue(delivery.insert("你好", target: proxy.documentIdentifier))

        XCTAssertEqual(proxy.native.text, "前😀你好后")
        XCTAssertFalse(delivery.hasMarkedText)
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
    let documentIdentifier = UUID()
    var documentContextBeforeInput: String? { (native.text as NSString).substring(to: native.selectedRange.location) }
    var documentContextAfterInput: String? { (native.text as NSString).substring(from: NSMaxRange(native.selectedRange)) }
    var selectedText: String? { native.selectedRange.length == 0 ? nil : (native.text as NSString).substring(with: native.selectedRange) }
    var documentInputMode: UITextInputMode? { nil }
    var hasText: Bool { native.hasText }
    func insertText(_ text: String) { native.insertText(text) }
    func deleteBackward() { native.deleteBackward() }
    func setMarkedText(_ text: String, selectedRange: NSRange) { native.setMarkedText(text, selectedRange: selectedRange) }
    func unmarkText() { /* The remote host has not processed this notification yet. */ }
    func adjustTextPosition(byCharacterOffset offset: Int) {
        native.selectedRange = NSRange(location: max(0, min(native.text.utf16.count, native.selectedRange.location + offset)), length: 0)
    }
}

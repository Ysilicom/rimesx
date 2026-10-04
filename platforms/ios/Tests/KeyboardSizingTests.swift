import XCTest
import UIKit
import RimesCore

@MainActor final class KeyboardSizingTests: XCTestCase {
    func testFirstAppearanceComputesHeightBeforeInputViewHasBounds() throws {
        let keyboard = KeyboardViewController()
        keyboard.layoutNeedsInputModeSwitchKey = false
        keyboard.loadViewIfNeeded()
        keyboard.developmentResetPreferences(); keyboard.developmentChoose(.chord)
        keyboard.developmentSetLayout(.orthogonal)
        let input = try XCTUnwrap(keyboard.inputView)
        let host = UIView(frame: CGRect(x: 0, y: 0, width: 393, height: 900))
        host.addSubview(input)
        for width: CGFloat in [393, 852, 320] {
            host.bounds.size.width = width
            input.bounds = .zero
            // The first sizing query precedes the input view's first layout.
            keyboard.viewWillAppear(false)
            keyboard.updateViewConstraints()
            let expected = KeyboardGeometry.height(layout: .orthogonal, chord: true, numeric: false,
                emoji: false, landscape: width > 600, width: width - 10) + 10 + 32 + (width > 600 ? 34 : 40) + 8 - 3
            for proposedHeight: CGFloat in [0, 600, 900] {
                let fitted = input.systemLayoutSizeFitting(CGSize(width: width, height: proposedHeight),
                    withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel)
                XCTAssertEqual(fitted.width, width, accuracy: 0.5)
                XCTAssertEqual(fitted.height, expected, accuracy: 0.5)
            }
        }
    }

    func testOrdinaryCustomLayoutComputesHeightBeforeFirstLayoutWithBuffer() throws {
        let keyboard = KeyboardViewController()
        keyboard.layoutNeedsInputModeSwitchKey = false
        keyboard.loadViewIfNeeded(); keyboard.developmentResetPreferences()
        let input = try XCTUnwrap(keyboard.inputView)
        let host = UIView(frame: CGRect(x: 0, y: 0, width: 393, height: 900))
        host.addSubview(input)
        for layout in CustomKeyboardLayout.templates {
            keyboard.developmentSetCustomLayout(layout)
            for width: CGFloat in [320, 393, 852] {
                host.bounds.size.width = width
                for buffering in [false, true] {
                    keyboard.developmentBuffer(buffering ? "尺寸检查" : nil)
                    input.bounds = .zero
                    keyboard.updateViewConstraints()
                    let keyHeight = layout.geometry(width: Double(width - 10), landscape: width > 600).height
                    let expected = keyHeight + 32 + 10 + 4 + (buffering ? (width > 600 ? 64 : 80) : 0)
                    let fitted = input.systemLayoutSizeFitting(CGSize(width: width, height: 900),
                        withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel)
                    XCTAssertEqual(fitted.width, width, accuracy: 0.5)
                    XCTAssertEqual(fitted.height, expected, accuracy: 0.5)
                }
            }
        }
    }

    func testUIKitPresentsKeyboardAtContentHeightOnFirstFocus() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first { $0.isKeyWindow }
        let window = UIWindow(windowScene: scene)
        let parent = UIViewController()
        window.rootViewController = parent; window.makeKeyAndVisible()
        let field = SizingTestTextField(frame: CGRect(x: 20, y: 100, width: 300, height: 44))
        parent.view.addSubview(field)
        field.keyboard.loadViewIfNeeded()
        field.keyboard.view.frame = CGRect(x: 0, y: 0, width: scene.coordinateSpace.bounds.width, height: 600)
        defer {
            field.resignFirstResponder(); window.isHidden = true
            previousKeyWindow?.makeKeyAndVisible()
        }
        // UIKit owns the container and all its constraints in this test.
        XCTAssertTrue(field.becomeFirstResponder())
        try await Task.sleep(nanoseconds: 500_000_000)
        let keyboard = field.keyboard
        XCTAssertNotNil(keyboard.view.window)
        XCTAssertGreaterThan(keyboard.view.bounds.width, 300)
        XCTAssertGreaterThan(keyboard.view.bounds.height, 140)
        XCTAssertLessThan(keyboard.view.bounds.height, 300)
        let candidates = keyboard.layoutViews.candidates
        XCTAssertEqual(candidates.convert(candidates.bounds, to: keyboard.view).minY, 5, accuracy: 0.5)
    }

    func testFirstMountDiscardsProvisionalHostHeight() throws {
        for width: CGFloat in [320, 393, 852] {
            let window = UIWindow(frame: CGRect(x: 0, y: 0, width: width, height: 900))
            let parent = UIViewController()
            window.rootViewController = parent; window.makeKeyAndVisible()
            defer { window.isHidden = true }

            let keyboard = KeyboardViewController()
            keyboard.layoutNeedsInputModeSwitchKey = false
            keyboard.loadViewIfNeeded()
            keyboard.developmentResetPreferences()
            keyboard.developmentChoose(.chord)
            // Do not disable autoresizing-mask constraints in this fixture: the
            // keyboard must reject the provisional height itself on first attachment.
            keyboard.view.frame = CGRect(x: 0, y: 300, width: width, height: 600)
            parent.addChild(keyboard); parent.view.addSubview(keyboard.view)
            keyboard.didMove(toParent: parent)
            parent.view.layoutIfNeeded(); keyboard.view.layoutIfNeeded()

            let views = keyboard.layoutViews
            let top = views.candidates.convert(views.candidates.bounds, to: keyboard.view).minY
            XCTAssertEqual(keyboard.view.bounds.width, width, accuracy: 0.5)
            XCTAssertEqual(top, 5, accuracy: 0.5, "Cold mount at width \(width)")
            XCTAssertLessThan(keyboard.view.bounds.height, 300)
            XCTAssertEqual(views.bottom.frame.maxY, keyboard.view.bounds.height - 5, accuracy: 0.5)
            XCTAssertTrue(try XCTUnwrap(keyboard.inputView).allowsSelfSizing)
        }
    }

    func testHeightFollowsContentAndRotationAfterSystemSizing() throws {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 900, height: 900))
        let parent = UIViewController()
        window.rootViewController = parent; window.makeKeyAndVisible()
        defer { window.isHidden = true }
        let keyboard = KeyboardViewController()
        keyboard.layoutNeedsInputModeSwitchKey = false
        parent.addChild(keyboard); parent.view.addSubview(keyboard.view)
        // Exercise layout changes under explicit host constraints; the other tests
        // cover UIKit's frame-managed presentation and self-sizing negotiation.
        keyboard.view.translatesAutoresizingMaskIntoConstraints = false
        let width = keyboard.view.widthAnchor.constraint(equalToConstant: 393)
        NSLayoutConstraint.activate([
            width,
            keyboard.view.leadingAnchor.constraint(equalTo: parent.view.leadingAnchor),
            keyboard.view.bottomAnchor.constraint(equalTo: parent.view.bottomAnchor)
        ])
        keyboard.didMove(toParent: parent)
        keyboard.developmentResetPreferences(); keyboard.developmentChoose(.chord)
        keyboard.developmentContent(); keyboard.developmentBuffer(nil)
        parent.view.layoutIfNeeded()
        let idleHeight = keyboard.view.bounds.height

        func checkContentFits(file: StaticString = #filePath, line: UInt = #line) throws {
            parent.view.layoutIfNeeded(); keyboard.view.layoutIfNeeded()
            let views = keyboard.layoutViews
            let topView = views.buffer.isHidden ? try XCTUnwrap(views.candidates.superview) : views.buffer
            XCTAssertEqual(topView.frame.minY, 5, accuracy: 0.5, file: file, line: line)
            XCTAssertEqual(views.bottom.frame.maxY, keyboard.view.bounds.height - 5, accuracy: 0.5, file: file, line: line)
            let input = try XCTUnwrap(keyboard.inputView)
            for proposed in [UIView.layoutFittingCompressedSize, CGSize(width: width.constant, height: 600)] {
                let fitted = input.systemLayoutSizeFitting(proposed)
                XCTAssertEqual(fitted.height, keyboard.view.bounds.height, accuracy: 0.5, file: file, line: line)
            }
        }
        try checkContentFits()

        // A host frame change at the same width must not become the content height.
        keyboard.view.frame.size.height = 600
        XCTAssertEqual(keyboard.view.bounds.height, 600)
        parent.view.setNeedsLayout()
        try checkContentFits()
        XCTAssertEqual(keyboard.view.bounds.height, idleHeight, accuracy: 0.5)

        keyboard.developmentBuffer("测试高度。")
        try checkContentFits()
        XCTAssertEqual(keyboard.view.bounds.height, idleHeight + 80, accuracy: 0.5)
        keyboard.developmentContent(preedit: "ni", candidates: Array(repeating: "这是一个很长的候选词", count: 8))
        keyboard.layoutViews.candidates.onExpand?()
        try checkContentFits()
        XCTAssertGreaterThan(keyboard.view.bounds.height, idleHeight + 80)

        // Width changes must use the landscape row heights in the first layout pass.
        width.constant = 852
        try checkContentFits()
        XCTAssertEqual(keyboard.layoutViews.buffer.bounds.height, 60)
        XCTAssertEqual(keyboard.layoutViews.bottom.bounds.height, 34)
        width.constant = 393
        try checkContentFits()
        XCTAssertEqual(keyboard.layoutViews.buffer.bounds.height, 76)

        keyboard.layoutViews.candidates.onExpand?()
        keyboard.developmentBuffer(nil); keyboard.developmentContent()
        try checkContentFits()
        XCTAssertEqual(keyboard.view.bounds.height, idleHeight, accuracy: 0.5)
        keyboard.beginAppearanceTransition(false, animated: false); keyboard.endAppearanceTransition()
        keyboard.view.frame.size.height = 600
        keyboard.beginAppearanceTransition(true, animated: false); keyboard.endAppearanceTransition()
        parent.view.setNeedsLayout()
        try checkContentFits()
        XCTAssertLessThan(keyboard.view.bounds.height, 300)
    }
}

@MainActor private final class SizingTestTextField: UITextField {
    let keyboard = KeyboardViewController()
    override var inputViewController: UIInputViewController? { keyboard }
}

import XCTest
import UIKit

// Diagnostic fixture, deliberately excluded from the shipping test target.
// See README.md for observed UIKit-only failures and reproduction instructions.
@MainActor final class UIKitHeightProbe: XCTestCase {
    func testNativeUIKitHeightChangeBaseline() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let old = scene.windows.first { $0.isKeyWindow }
        let window = UIWindow(windowScene: scene), parent = UIViewController()
        window.rootViewController = parent; window.makeKeyAndVisible()
        let field = NativeSizingField(frame: CGRect(x: 20, y: 100, width: 300, height: 44))
        parent.view.addSubview(field)
        field.keyboard.loadViewIfNeeded()
        field.keyboard.view.frame = CGRect(x: 0, y: 0, width: scene.coordinateSpace.bounds.width, height: 600)
        defer { field.resignFirstResponder(); window.isHidden = true; old?.makeKeyAndVisible() }
        _ = parent.view.keyboardLayoutGuide
        XCTAssertTrue(field.becomeFirstResponder())
        try await Task.sleep(nanoseconds: 500_000_000)
        for height: CGFloat in [200, 280, 346, 280, 200] {
            field.keyboard.height.constant = height
            field.keyboard.view.invalidateIntrinsicContentSize()
            field.keyboard.view.setNeedsLayout()
            try await Task.sleep(nanoseconds: 300_000_000)
            let guide = parent.view.keyboardLayoutGuide.layoutFrame.height
            print("SIZING_BASELINE expected=\(height) input=\(field.keyboard.view.bounds) guide=\(guide)")
            XCTAssertEqual(field.keyboard.view.bounds.height, height, accuracy: 0.5)
            XCTAssertEqual(guide, height, accuracy: 0.5)
        }
    }
}

@MainActor private final class NativeSizingField: UITextField {
    let keyboard = NativeSizingController()
    override var inputViewController: UIInputViewController? { keyboard }
}
@MainActor private final class NativeSizingController: UIInputViewController {
    var height: NSLayoutConstraint!
    override func viewDidLoad() {
        super.viewDidLoad()
        view.translatesAutoresizingMaskIntoConstraints = false
        inputView?.allowsSelfSizing = true
        height = view.heightAnchor.constraint(equalToConstant: 200)
        height.isActive = true
        let label = UILabel(); label.text = "Native sizing baseline"
        label.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(label)
        NSLayoutConstraint.activate([label.centerXAnchor.constraint(equalTo: view.centerXAnchor), label.centerYAnchor.constraint(equalTo: view.centerYAnchor)])
    }
}

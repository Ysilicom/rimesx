import Foundation

// SwiftPM's native accessor looks beside the executable or in the build tree.
// Packaged macOS apps place resource bundles in Contents/Resources instead.
enum RimesCoreResources {
    static let bundle: Bundle = {
        for root in [Bundle.main.resourceURL, Bundle.main.bundleURL].compactMap({ $0 }) {
            if let bundle = Bundle(url: root.appendingPathComponent("RimesCore_RimesCore.bundle")) {
                return bundle
            }
        }
        return Bundle.module
    }()
}

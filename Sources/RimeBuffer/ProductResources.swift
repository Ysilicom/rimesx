import Foundation

enum ProductResources {
    static let bundle: Bundle = {
        for root in [Bundle.main.resourceURL, Bundle.main.bundleURL].compactMap({ $0 }) {
            if let bundle = Bundle(url: root.appendingPathComponent("RimeBuffer_RimeBuffer.bundle")) {
                return bundle
            }
        }
        return Bundle.module
    }()
}

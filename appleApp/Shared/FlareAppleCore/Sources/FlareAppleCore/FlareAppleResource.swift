import Foundation

private final class FlareAppleResourceBundleToken {}

public enum FlareAppleResource {
    public static let bundle: Bundle = {
        let bundleName = "FlareAppleResource.bundle"
        let tokenBundle = Bundle(for: FlareAppleResourceBundleToken.self)
        let bundleURLs = [
            Bundle.main.resourceURL?.appendingPathComponent(bundleName),
            Bundle.main.bundleURL.appendingPathComponent(bundleName),
            tokenBundle.resourceURL?.appendingPathComponent(bundleName),
            tokenBundle.bundleURL.appendingPathComponent(bundleName),
        ]

        for bundleURL in bundleURLs {
            if let bundleURL, let bundle = Bundle(url: bundleURL) {
                return bundle
            }
        }

        return tokenBundle
    }()
}

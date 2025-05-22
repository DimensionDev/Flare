import SafariServices
import SwiftUI

@MainActor
public final class SafariManager: NSObject, SFSafariViewControllerDelegate {
    public static let shared: SafariManager = {
        Task { @MainActor in
            await SafariManager()
        }
        return SafariManager()
    }()

    private var windowScene: UIWindowScene?
    private let viewController: UIViewController = .init()
    private var window: UIWindow?

    override nonisolated init() {
        super.init()
    }

    @MainActor
    public func open(_ url: URL) -> OpenURLAction.Result {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
            return .systemAction
        }

        window = setupWindow(windowScene: windowScene)

        let configuration = SFSafariViewController.Configuration()
        configuration.entersReaderIfAvailable = true // 默认启用阅读模式

        let safari = SFSafariViewController(url: url, configuration: configuration)
        // safari.preferredBarTintColor = .systemBackground
        safari.preferredControlTintColor = .systemBlue
        safari.delegate = self

        DispatchQueue.main.async { [weak self] in
            self?.viewController.present(safari, animated: true)
        }

        return .handled
    }

    private func setupWindow(windowScene: UIWindowScene) -> UIWindow {
        let window = window ?? UIWindow(windowScene: windowScene)
        window.rootViewController = viewController
        window.makeKeyAndVisible()
        window.overrideUserInterfaceStyle = .unspecified
        self.window = window
        return window
    }

    public func dismiss() {
        viewController.presentedViewController?.dismiss(animated: true)
        window?.resignKey()
        window?.isHidden = false
        window = nil
    }

    public nonisolated func safariViewControllerDidFinish(_: SFSafariViewController) {
        Task { @MainActor in
            window?.resignKey()
            window?.isHidden = false
            window = nil
        }
    }
}

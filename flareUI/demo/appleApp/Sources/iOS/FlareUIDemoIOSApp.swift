@preconcurrency import FlareUI
import UIKit

@main
final class FlareUIDemoIOSApp: UIResponder, UIApplicationDelegate {
    private var host: FlareDemoHost?
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        let host = FlareDemoHost()
        let viewController = FlareUIKitDemoViewController(contentView: host.view)
        let navigationController = UINavigationController(rootViewController: viewController)
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = navigationController
        window.makeKeyAndVisible()

        self.host = host
        self.window = window
        return true
    }

    func applicationWillTerminate(_ application: UIApplication) {
        host?.dispose()
        host = nil
    }
}

private final class FlareUIKitDemoViewController: UIViewController {
    private let contentView: UIView

    init(contentView: UIView) {
        self.contentView = contentView
        super.init(nibName: nil, bundle: nil)
        title = "Flare UI · UIKit"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("Use init(contentView:) instead")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        contentView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(contentView)

        NSLayoutConstraint.activate([
            contentView.leadingAnchor.constraint(equalTo: view.layoutMarginsGuide.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: view.layoutMarginsGuide.trailingAnchor),
            contentView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            contentView.bottomAnchor.constraint(
                lessThanOrEqualTo: view.safeAreaLayoutGuide.bottomAnchor,
                constant: -24
            ),
        ])
    }
}

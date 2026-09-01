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
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = FlareUIKitDemoViewController(
            contentViewController: host.viewController
        )
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
    private let contentViewController: UIViewController

    init(contentViewController: UIViewController) {
        self.contentViewController = contentViewController
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        addChild(contentViewController)
        let contentView = contentViewController.view!
        contentView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(contentView)
        NSLayoutConstraint.activate([
            contentView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            contentView.topAnchor.constraint(equalTo: view.topAnchor),
            contentView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        contentViewController.didMove(toParent: self)
    }
}

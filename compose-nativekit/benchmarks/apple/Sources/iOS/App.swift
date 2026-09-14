import UIKit

@main @MainActor final class AppDelegate: UIResponder, UIApplicationDelegate {
    static func main() {
        if ProcessInfo.processInfo.arguments.contains("--headless") {
            setbuf(stdout, nil)
            let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 780))
            let controller = UIViewController()
            window.rootViewController = controller
            window.isHidden = false
            let viewport = UIView(frame: window.bounds)
            controller.view.addSubview(viewport)
            print("APPLE_LAZY_WINDOW,scene=detached,visible=false")
            Task { @MainActor in await BenchmarkRunner(container: viewport).run() }
            withExtendedLifetime(window) { RunLoop.main.run() }
        } else {
            UIApplicationMain(CommandLine.argc, CommandLine.unsafeArgv, nil, NSStringFromClass(AppDelegate.self))
        }
    }

    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        setbuf(stdout, nil)
        print("APPLE_LAZY_APP_LAUNCHED")
        return true
    }
    func application(_ application: UIApplication, configurationForConnecting session: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: "Benchmark", sessionRole: session.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }
}

@MainActor final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    private var viewport: UIView?
    private var started = false
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options: UIScene.ConnectionOptions) {
        guard let scene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: scene)
        let controller = UIViewController()
        controller.view.backgroundColor = .systemBackground
        let viewport = UIView(frame: CGRect(x: 0, y: 0, width: 390, height: 780))
        controller.view.addSubview(viewport)
        window.rootViewController = controller
        window.makeKeyAndVisible()
        self.window = window
        self.viewport = viewport
    }
    func sceneDidBecomeActive(_ scene: UIScene) {
        guard !started, let viewport else { return }
        started = true
        Task { @MainActor in await BenchmarkRunner(container: viewport).run() }
    }
}

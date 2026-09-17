import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI

@main
struct FlareApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        MediaCacheMaintenance.configure()
        configureAudioSessionForMixing()
        let firebaseEnabled = FirebaseBootstrap.configureIfAvailable()
        if firebaseEnabled {
            AppleSharedHelper.shared.setupCrashlytics()
        }
        AppleSharedHelper.shared.initialize(
            inAppNotification: SwiftInAppNotification.shared,
            swiftFormatter: Formatter.shared,
            swiftPlatformTextRenderer: PlatformTextRenderer.shared,
            swiftOnDeviceAI: FoundationModelOnDeviceAI.shared
        )
    }
    var body: some Scene {
        WindowGroup {
            FlareTheme {
                if #available(iOS 18.0, *) {
                    FlareRoot()
                } else {
                    BackportFlareRoot()
                }
            }
            .onChange(of: scenePhase) { _, phase in
                MediaCacheMaintenance.handleScenePhase(phase)
            }
        }
    }
    
    func configureAudioSessionForMixing() {
        AudioSessionManager.shared.activateAmbient()
    }
}

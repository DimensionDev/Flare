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
                root
            }
            .modifier(IOSTimelineListEnvironment())
            .onChange(of: scenePhase) { _, phase in
                MediaCacheMaintenance.handleScenePhase(phase)
            }
        }
    }

    @ViewBuilder
    private var root: some View {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--media-viewer-test") {
            MediaViewerTestFixture()
        } else {
            appRoot
        }
        #else
        appRoot
        #endif
    }

    @ViewBuilder
    private var appRoot: some View {
        if #available(iOS 18.0, *) {
            FlareRoot()
        } else {
            BackportFlareRoot()
        }
    }
    
    func configureAudioSessionForMixing() {
        AudioSessionManager.shared.activateAmbient()
    }
}

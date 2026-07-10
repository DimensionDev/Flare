import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import AVFAudio

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

/// Centralized AVAudioSession category control.
///
/// Default is `.ambient` so timeline autoplay videos don't prevent auto-lock and
/// respect the silent switch. Full-screen media playback can ref-count a request
/// for `.playback` (uninterrupted audio, ignores silent switch) and revert when
/// done.
final class AudioSessionManager {
    static let shared = AudioSessionManager()

    private var playbackRequestCount = 0

    private init() {}

    func activateAmbient() {
        applyCategory(.ambient)
        do {
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("Audio session error: \(error)")
        }
    }

    func beginPlayback() {
        playbackRequestCount += 1
        if playbackRequestCount == 1 {
            applyCategory(.playback)
        }
    }

    func endPlayback() {
        guard playbackRequestCount > 0 else { return }
        playbackRequestCount -= 1
        if playbackRequestCount == 0 {
            applyCategory(.ambient)
        }
    }

    private func applyCategory(_ category: AVAudioSession.Category) {
        let session = AVAudioSession.sharedInstance()
        do {
            if #available(iOS 17, *) {
                try session.setCategory(category, mode: .default, policy: .default, options: [.mixWithOthers])
            } else {
                try session.setCategory(category, options: [.mixWithOthers])
            }
        } catch {
            print("Audio session error: \(error)")
        }
    }
}

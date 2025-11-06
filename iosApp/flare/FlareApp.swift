import SwiftUI
import KotlinSharedUI
import AVFAudio

@main
struct FlareApp: App {
    init() {
        configureAudioSessionForMixing()
        ComposeUIHelper.shared.initialize(
            inAppNotification: SwiftInAppNotification.shared,
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
        }
    }
    
    func configureAudioSessionForMixing() {
        let session = AVAudioSession.sharedInstance()
        do {
            if #available(iOS 17, *) {
                try session.setCategory(.playback, mode: .default, policy: .default, options: [.mixWithOthers])
            } else {
                try session.setCategory(.playback, options: [.mixWithOthers])
            }
            try session.setActive(true)
        } catch {
            print("Audio session error: \(error)")
        }
    }
}

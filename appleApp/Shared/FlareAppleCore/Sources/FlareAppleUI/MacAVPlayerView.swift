#if os(macOS)
import AVFoundation
import AVKit
import SwiftUI

struct MacAVPlayerView: NSViewRepresentable {
    let player: AVPlayer
    var videoGravity: AVLayerVideoGravity = .resizeAspect
    var showsControls = false
    var canDisplayFrame = true
    var onReady: () -> Void = {}

    final class Coordinator {
        var observation: NSKeyValueObservation?
        var onReady: () -> Void = {}
        var canDisplayFrame = true
        var reportedReady = false

        func update(_ view: AVPlayerView) {
            let ready = canDisplayFrame && view.player != nil && view.isReadyForDisplay
            view.alphaValue = ready ? 1 : 0
            if ready, !reportedReady {
                reportedReady = true
                onReady()
            }
        }

        func observe(_ view: AVPlayerView) {
            view.alphaValue = 0
            observation = view.observe(\.isReadyForDisplay, options: [.initial, .new]) { [weak self] view, _ in
                Task { @MainActor [weak self, weak view] in
                    guard let self, let view else { return }
                    self.update(view)
                }
            }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeNSView(context: Context) -> AVPlayerView {
        let view = AVPlayerView()
        context.coordinator.onReady = onReady
        context.coordinator.canDisplayFrame = canDisplayFrame
        context.coordinator.observe(view)
        view.player = player
        view.videoGravity = videoGravity
        view.controlsStyle = showsControls ? .floating : .none
        view.allowsPictureInPicturePlayback = showsControls
        return view
    }

    func updateNSView(_ view: AVPlayerView, context: Context) {
        context.coordinator.onReady = onReady
        context.coordinator.canDisplayFrame = canDisplayFrame
        if view.player !== player {
            context.coordinator.reportedReady = false
            view.alphaValue = 0
            view.player = player
        }
        view.videoGravity = videoGravity
        view.controlsStyle = showsControls ? .floating : .none
        view.allowsPictureInPicturePlayback = showsControls
        context.coordinator.update(view)
    }

    static func dismantleNSView(_ view: AVPlayerView, coordinator: Coordinator) {
        coordinator.observation = nil
        coordinator.onReady = {}
        view.player = nil
    }
}

#endif

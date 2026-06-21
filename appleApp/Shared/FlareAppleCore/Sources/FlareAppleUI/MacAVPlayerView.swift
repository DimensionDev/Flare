#if os(macOS)
import AVFoundation
import AVKit
import SwiftUI

struct MacAVPlayerView: NSViewRepresentable {
    let player: AVPlayer
    var videoGravity: AVLayerVideoGravity = .resizeAspect
    var showsControls = false

    func makeNSView(context: Context) -> AVPlayerView {
        let view = AVPlayerView()
        view.player = player
        view.videoGravity = videoGravity
        view.controlsStyle = showsControls ? .floating : .none
        view.allowsPictureInPicturePlayback = showsControls
        return view
    }

    func updateNSView(_ view: AVPlayerView, context: Context) {
        if view.player !== player {
            view.player = player
        }
        view.videoGravity = videoGravity
        view.controlsStyle = showsControls ? .floating : .none
        view.allowsPictureInPicturePlayback = showsControls
    }
}

enum MacAVPlayerPlayback {
    nonisolated static func replay(_ player: AVPlayer, rate: Float) {
        player.seek(to: .zero, toleranceBefore: .zero, toleranceAfter: .zero) { finished in
            guard finished else {
                return
            }
            DispatchQueue.main.async {
                player.playImmediately(atRate: rate)
            }
        }
    }
}
#endif

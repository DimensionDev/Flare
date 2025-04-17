import AVKit
import ExyteChat
import Kingfisher
import ObjectiveC
import shared
import SwiftUI
import CoreMedia


/// 音频波形数据缓存
// var waveformCache: [String: [CGFloat]] = [:] // Consider if cache is needed with pre-calculated data

/// 音频播放器状态
private class AudioPlayerState: ObservableObject {
    @Published var isPlaying = false
    @Published var progress: Double = 0
    @Published var duration: Double = 0
    @Published var currentTime: Double = 0 // Added current time
    
    private var player: AVPlayer?
    private var timeObserver: Any?
    
    func play(url: URL, headers: [String: String]? = nil) {
        stop() // Ensure clean state
        let asset = AVURLAsset(url: url, options: headers.map { ["AVURLAssetHTTPHeaderFieldsKey": $0] })
        let item = AVPlayerItem(asset: asset)
        player = AVPlayer(playerItem: item)
        
        timeObserver = player?.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.1, preferredTimescale: 10), queue: .main) { [weak self] time in
            guard let self = self, let currentItem = self.player?.currentItem else { return }
            let durationSec = currentItem.duration.seconds
            let currentTimeSec = time.seconds
            
            // Update duration if valid and changed
            if !durationSec.isNaN, durationSec > 0, self.duration != durationSec {
                self.duration = durationSec
            }
            
            // Update current time and progress
            if !currentTimeSec.isNaN {
                 self.currentTime = currentTimeSec
                 if self.duration > 0 { // Avoid division by zero
                     self.progress = currentTimeSec / self.duration
                 } else {
                     self.progress = 0
                 }
            }
        }
        
        NotificationCenter.default.addObserver(self, selector: #selector(playerDidFinishPlaying), name: .AVPlayerItemDidPlayToEndTime, object: item)
        player?.play()
        isPlaying = true
    }
    
    func stop() {
        player?.pause()
        player = nil
        if let observer = timeObserver {
            timeObserver = nil // Just nullify, let player dealloc handle removal if needed
        }
        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemDidPlayToEndTime, object: nil)
        isPlaying = false
        progress = 0
        duration = 0
        currentTime = 0 // Reset current time
    }
    
    func togglePlay(url: URL, headers: [String: String]? = nil) {
        if isPlaying {
            stop()
        } else {
            play(url: url, headers: headers)
        }
    }
    
    @objc private func playerDidFinishPlaying(notification: NSNotification) {
         DispatchQueue.main.async { [weak self] in
             self?.stop()
         }
     }
    
    deinit {
        stop()
    }
}

/// 音频波形视图
private struct WaveformView: View {
    let samples: [CGFloat]
    let progress: Double
    let color: Color
    
    var body: some View {
        GeometryReader { geometry in
            HStack(alignment: .center, spacing: 2) {
                ForEach(Array(samples.enumerated()), id: \.offset) { index, sample in
                    Capsule()
                        .fill(index < Int(Double(samples.count) * progress) ? color : color.opacity(0.3))
                        .frame(width: 3, height: max(4, geometry.size.height * sample))
                }
            }
            .frame(maxHeight: .infinity)
        }
    }
}

/// 音频消息视图
struct DMAudioMessageView: View {
    let url: URL
    let media: UiMedia
    let isCurrentUser: Bool
    @StateObject private var playerState = AudioPlayerState()
    @State private var waveformSamples: [CGFloat] = Array(repeating: 0.05, count: 50)
    
    var body: some View {
        HStack(spacing: 8) {
            Button(action: {
                playerState.togglePlay(url: url, headers: media.customHeaders)
            }) {
                Image(systemName: playerState.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                    .resizable()
                    .frame(width: 24, height: 24)
                    .foregroundColor(isCurrentUser ? .white : .primary)
            }
            .layoutPriority(1) // Give button higher priority to keep its size
            
            WaveformView(
                samples: waveformSamples,
                progress: playerState.progress,
                color: isCurrentUser ? .white : .primary
            )
            .frame(height: 24)
            // Limit WaveformView's maximum width to prevent overlap
            // Experiment with this value based on typical bubble widths
            .frame(maxWidth: 150) // Example maximum width
            .clipped() // Ensure capsules don't draw outside the frame
            .layoutPriority(0) // Give waveform lower priority than button/text

            Spacer(minLength: 4) // Ensure at least some space
            
            // Display Current Time / Total Duration
            Text("\(formatDuration(playerState.currentTime)) / \(formatDuration(playerState.duration))")
                .font(.caption)
                .foregroundColor(isCurrentUser ? .white.opacity(0.7) : .gray)
                .lineLimit(1)
                .fixedSize()
                .layoutPriority(1)
                .padding(.trailing, 4)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }

    /// Formats duration in seconds to MM:SS or HH:MM:SS
    private func formatDuration(_ duration: Double) -> String {
        guard duration > 0, !duration.isNaN else {
            return "--:--"
        }
        let formatter = DateComponentsFormatter()
        formatter.allowedUnits = [.hour, .minute, .second]
        formatter.unitsStyle = .positional
        formatter.zeroFormattingBehavior = .pad
        if duration < 3600 {
            formatter.allowedUnits = [.minute, .second]
        }
        return formatter.string(from: TimeInterval(duration)) ?? "--:--"
    }
}

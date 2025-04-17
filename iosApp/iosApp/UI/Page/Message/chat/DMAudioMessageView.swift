import AVKit
import ExyteChat
import Kingfisher
import ObjectiveC
import shared
import SwiftUI
import CoreMedia


/// 音频波形数据缓存
  var waveformCache: [String: [CGFloat]] = [:]

/// 音频播放器状态
private class AudioPlayerState: ObservableObject {
    @Published var isPlaying = false
    @Published var progress: Double = 0
    @Published var duration: Double = 0
    
    private var player: AVPlayer?
    private var timeObserver: Any?
    
    func play(url: URL, headers: [String: String]? = nil) {
        stop()
        let asset = AVURLAsset(url: url, options: headers.map { ["AVURLAssetHTTPHeaderFieldsKey": $0] })
        let item = AVPlayerItem(asset: asset)
        player = AVPlayer(playerItem: item)
        
        // 监听播放进度
        timeObserver = player?.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.1, preferredTimescale: 10),
            queue: .main
        ) { [weak self] time in
            guard let self = self,
                  let duration = self.player?.currentItem?.duration.seconds,
                  !duration.isNaN
            else { return }
            
            self.duration = duration
            self.progress = time.seconds / duration
        }
        
        // 监听播放完成
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(playerDidFinishPlaying),
            name: .AVPlayerItemDidPlayToEndTime,
            object: item
        )
        
        player?.play()
        isPlaying = true
    }
    
    func stop() {
        player?.pause()
        player = nil
        if let observer = timeObserver {
            player?.removeTimeObserver(observer)
            timeObserver = nil
        }
        isPlaying = false
        progress = 0
    }
    
    func togglePlay(url: URL, headers: [String: String]? = nil) {
        if isPlaying {
            stop()
        } else {
            play(url: url, headers: headers)
        }
    }
    
    @objc private func playerDidFinishPlaying() {
        stop()
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
    @State private var waveformSamples: [CGFloat] = []
    @State private var isLoading = true
    
    var body: some View {
        VStack {
            if isLoading {
                ProgressView()
            } else {
                HStack(spacing: 8) {
                    Button(action: {
                        playerState.togglePlay(url: url, headers: media.customHeaders)
                    }) {
                        Image(systemName: playerState.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .resizable()
                            .frame(width: 24, height: 24)
                            .foregroundColor(isCurrentUser ? .white : .primary)
                    }
                    
                    WaveformView(
                        samples: waveformSamples,
                        progress: playerState.progress,
                        color: isCurrentUser ? .white : .primary
                    )
                    .frame(height: 24)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(isCurrentUser ? Color.accentColor : Color.gray.opacity(0.1))
                .cornerRadius(12)
            }
        }
        .task {
            do {
                waveformSamples = try await generateWaveformSamples(from: url, headers: media.customHeaders)
                isLoading = false
            } catch {
                print("Error generating waveform: \(error)")
                isLoading = false
            }
        }
    }
    
    private func generateWaveformSamples(from url: URL, headers: [String: String]?) async throws -> [CGFloat] {
        let asset = AVURLAsset(url: url, options: headers.map { ["AVURLAssetHTTPHeaderFieldsKey": $0] })
        let audioTrack = try await asset.loadTracks(withMediaType: .audio).first
        guard let audioTrack = audioTrack else {
            throw NSError(domain: "AudioMessageView", code: -1, userInfo: [NSLocalizedDescriptionKey: "No audio track found"])
        }
        
        let reader = try AVAssetReader(asset: asset)
        let output = AVAssetReaderTrackOutput(
            track: audioTrack,
            outputSettings: [
                AVFormatIDKey: kAudioFormatLinearPCM,
                AVLinearPCMBitDepthKey: 16,
                AVLinearPCMIsBigEndianKey: false,
                AVLinearPCMIsFloatKey: false,
                AVLinearPCMIsNonInterleaved: false
            ]
        )
        
        reader.add(output)
        reader.startReading()
        
        var samples: [CGFloat] = []
        let sampleCount = 50 // 采样点数量
        var audioSamples: [Float] = []
        
        while let sampleBuffer = output.copyNextSampleBuffer(),
              let blockBuffer = CMSampleBufferGetDataBuffer(sampleBuffer) {
            var length = 0
            var dataPointer: UnsafeMutablePointer<Int8>?
            
            guard CMBlockBufferGetDataPointer(
                blockBuffer,
                atOffset: 0,
                lengthAtOffsetOut: &length,
                totalLengthOut: nil,
                dataPointerOut: &dataPointer
            ) == noErr else { continue }
            
            let samples16bit = UnsafeBufferPointer<Int16>(
                start: UnsafeRawPointer(dataPointer)?.assumingMemoryBound(to: Int16.self),
                count: length / 2
            )
            
            let maxAmplitude = Float(Int16.max)
            for sample in samples16bit {
                let normalizedSample = Float(abs(sample)) / maxAmplitude
                audioSamples.append(normalizedSample)
            }
        }
        
        // 将采样数据平均分配到50个点
        let samplesPerPoint = max(1, audioSamples.count / sampleCount)
        for i in 0..<sampleCount {
            let start = i * samplesPerPoint
            let end = min(start + samplesPerPoint, audioSamples.count)
            if start < audioSamples.count {
                let sum = audioSamples[start..<end].reduce(0, +)
                let average = sum / Float(end - start)
                samples.append(CGFloat(average))
            } else {
                samples.append(0)
            }
        }
        
        return samples
    }
}

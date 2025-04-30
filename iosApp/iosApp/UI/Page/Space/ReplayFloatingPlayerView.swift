import AVFoundation
import Combine
import shared
import SwiftUI

struct ReplayFloatingPlayerView: View {
    @ObservedObject var manager: IOSPodcastManager

    @State private var displayTime: Double = 0.0
    @State private var isEditingSlider: Bool = false

    private var isFailedState: Bool {
        if case .failed = manager.playbackState { true } else { false }
    }

    var body: some View {
        Group {
            if let podcast = manager.currentPodcast {
                VStack(spacing: 0) {
                    playerControls(podcast: podcast)
                    progressBar
                }
                .background(isFailedState ? Color.red.opacity(0.1) : Color.clear)
                .background(Color(uiColor: .systemGray5).opacity(0.9))
                .cornerRadius(10)
                .padding(.horizontal)
                .padding(.bottom, 5)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .onReceive(manager.currentTimePublisher) { receivedTime in
                    if !isEditingSlider, !manager.isSeeking {
                        displayTime = receivedTime
                    }
                }
                .onAppear {
                    displayTime = manager.currentPlaybackTime
                }
                .onChange(of: manager.currentPodcast?.id) { _ in
                    displayTime = manager.currentPlaybackTime
                }
            }
        }
    }

    @ViewBuilder
    private func playerControls(podcast: UiPodcast) -> some View {
        HStack(spacing: 8) {
            UserAvatar(data: podcast.creator.avatar, size: 24)
                .padding(.leading, 8)

            Text(podcast.title)
                .font(.footnote)
                .lineLimit(1)

            Spacer()

            Group {
                switch manager.playbackState {
                case .loading:
                    ProgressView()
                        .frame(width: 28, height: 28)
                case .failed:
                    Image(systemName: "exclamationmark.triangle.fill")
                case .playing, .paused, .stopped:
                    Button {
                        if manager.playbackState == .playing {
                            manager.pause()
                        } else {
                            manager.resume()
                        }
                    } label: {
                        Image(systemName: manager.playbackState == .playing ? "pause.fill" : "play.fill")
                            .font(.title2)
                    }
                    .disabled(manager.playbackState == .loading)
                }
            }

            Button {
                manager.stopPodcast()
            } label: {
                Image(systemName: "xmark")
                    .font(.body)
                    .foregroundColor(.secondary)
            }
            .padding(.leading, 8)
            .padding(.trailing, 12)
        }
        .frame(height: 44)
    }

    @ViewBuilder
    private var progressBar: some View {
        VStack(spacing: 2) {
            Slider(value: $displayTime, in: 0 ... (manager.duration ?? 1.0), onEditingChanged: sliderEditingChanged)
                .padding(.horizontal)
                .frame(height: 15)
                .disabled(isFailedState || manager.duration == nil || !manager.canSeek)
                .accentColor(isFailedState ? .gray : .primary)
                .controlSize(.small)

            HStack {
                Text(formatTime(displayTime))
                Spacer()
                Text(formatTime(manager.duration ?? 0))
            }
            .font(.caption2)
            .foregroundColor(.secondary)
            .padding(.horizontal)
            .padding(.top, 8)
            .padding(.bottom, 5)
        }
    }

    private func sliderEditingChanged(editing: Bool) {
        isEditingSlider = editing
        if !editing {
            manager.seek(to: displayTime)
        }
    }

    private func formatTime(_ time: Double) -> String {
        if time.isNaN || time.isInfinite || time < 0 {
            return "--:--"
        }
        let totalSeconds = Int(time)
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }
}

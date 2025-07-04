import AVFoundation
import shared
import SwiftUI

struct LiveFloatingPlayerView: View {
    @StateObject private var manager = IOSPodcastManager.shared
    @Environment(FlareRouter.self) private var router

    @State private var showPodcastSheet: Bool = false

    private var isFailedState: Bool {
        if case .failed = manager.playbackState { true } else { false }
    }

    var body: some View {
        HStack {
            if let podcast = manager.currentPodcast {
                VStack(spacing: 0) {
                    playerControls(podcast: podcast)
                    // progressBar
                }
                .background(isFailedState ? Color.red.opacity(0.1) : Color.clear)
                .background(Color(uiColor: .systemGray5).opacity(0.9))
                .cornerRadius(10)
                .padding(.horizontal)
                .padding(.bottom, 5)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .onTapGesture {
                    showPodcastSheet = true
//                    router
//                        .navigate(
//                            to:
//                            .podcastSheet(
//                                accountType: UserManager.shared
//                                    .getCurrentAccountType()!,
//                                podcastId: podcast.id
//                            )
//                        )
                }.sheet(isPresented: $showPodcastSheet) {
                    if let podcastId = manager.currentPodcast?.id {
                        PodcastSheetView(
                            accountType: UserManager.shared.getCurrentAccountType()!,
                            podcastId: podcastId
                        )
                    } else {
                        // Handle the case where podcastId is nil, perhaps show an error or an empty state
                        Text("Error: Podcast ID not available.")
                    }
                }
                // .onChange(of: manager.currentTime) { ... }
                // .onAppear { ... }
                // .onChange(of: manager.currentPodcast?.id) { ... }
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

            Text("LIVE")
                .font(.caption.weight(.bold))
                .foregroundColor(.red)
                .padding(.horizontal, 5)
                .padding(.vertical, 2)
                .background(Color.red.opacity(0.15))
                .cornerRadius(4)
                .layoutPriority(1)

            Spacer()

            Group {
                switch manager.playbackState {
                case .loading:
                    ProgressView()
                        .frame(width: 28, height: 28)
                case .failed:
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.red)
                        .font(.title2)
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
}

import shared
import SwiftUI

struct PodcastSheetView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(FlareRouter.self) private var router
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareTheme.self) private var theme

    private let presenter: PodcastPresenter
    let accountType: AccountType
    let podcastId: String

    init(accountType: AccountType, podcastId: String) {
        self.accountType = accountType
        self.podcastId = podcastId
        presenter = PodcastPresenter(accountType: accountType, id: podcastId)
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            NavigationView {
                VStack {
                    let uiState = state.data

                    if uiState is UiStateLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if let success = uiState as? UiStateSuccess<UiPodcast> {
                        PodcastDetailContent(podcast: success.data, accountType: accountType)
                    } else {
                        ProgressView()
                    }
                }
                .background(theme.primaryBackgroundColor)
                .navigationTitle("Podcast")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("Close") {
                            dismiss()
                        }
                    }
                }
            }
        }
    }
}

struct PodcastDetailContent: View {
    let podcast: UiPodcast
    @Environment(FlareRouter.self) private var router
    let accountType: AccountType
    @Environment(FlareTheme.self) private var theme

    let columns: [GridItem] = Array(repeating: .init(.flexible()), count: 4)

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(podcast.title).font(.title2).padding(.bottom)

                //   Hosts
                if !podcast.hosts.isEmpty {
                    Text("Hosts (\(podcast.hosts.count))").font(.headline)
                    LazyVGrid(columns: columns, spacing: 16) {
                        ForEach(podcast.hosts, id: \.key.hash) { host in
                            VStack {
                                UserAvatar(data: host.avatar, size: 50)
                                    .onTapGesture {
                                        router.navigate(to: .profile(
                                            accountType: accountType,
                                            userKey: host.key
                                        ))
                                        router.dismissSheet()
                                    }
                                Text(host.name.raw)
                                    .font(.caption)
                                    .lineLimit(1)
                                Text("Host")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    .padding(.bottom)
                }

                //  Speakers
                if !podcast.speakers.isEmpty {
                    Text("Speakers (\(podcast.speakers.count))").font(.headline)
                    LazyVGrid(columns: columns, spacing: 16) {
                        ForEach(podcast.speakers, id: \.key.hash) { speaker in
                            VStack {
                                UserAvatar(data: speaker.avatar, size: 50)
                                    .onTapGesture {
                                        router.navigate(to: .profile(
                                            accountType: accountType,
                                            userKey: speaker.key
                                        ))
                                        router.dismissSheet()
                                    }
                                Text(speaker.name.raw)
                                    .font(.caption)
                                    .lineLimit(1)
                                Text("Speaker")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    .padding(.bottom)
                }

                Spacer()

                // Action Button
                if IOSPodcastManager.shared.currentPodcast != nil, IOSPodcastManager.shared.currentPodcast?.playbackUrl == podcast.playbackUrl {
                    Button {
                        IOSPodcastManager.shared.stopPodcast()
                        router.dismissSheet()
                    } label: {
                        Label("Stop Listening ", systemImage: "headphones")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(podcast.playbackUrl == "" || podcast.playbackUrl == nil)
                    .tint(.pink)
                } else {
                    Button {
                        IOSPodcastManager.shared.playPodcast(podcast: podcast)
                        router.dismissSheet()
                    } label: {
                        Label("Start Listening", systemImage: "headphones")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(podcast.playbackUrl == "" || podcast.playbackUrl == nil)
                    .tint(.pink)
                }

                if podcast.ended {
                    Text("This podcast url not available.")
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            }
            .listRowBackground(theme.primaryBackgroundColor)
            .padding()
        }
    }
}

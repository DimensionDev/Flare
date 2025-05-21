import Kingfisher
import shared
import SwiftUI

struct SpaceScreen: View {
    let accountType: AccountType
    @EnvironmentObject private var router: FlareRouter
    @State private var presenter: PodcastListPresenter
    @Environment(FlareTheme.self) private var theme

    init(accountType: AccountType) {
        self.accountType = accountType
        _presenter = .init(initialValue: PodcastListPresenter(accountType: accountType))
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            feedsListView(state)
                .navigationTitle("You follow the X Spaces")
                .navigationBarTitleDisplayMode(.inline)
                .listRowBackground(theme.primaryBackgroundColor)
        }
    }

    @ViewBuilder
    private func feedsListView(_ state: PodcastListPresenterState) -> some View {
        switch onEnum(of: state.data) {
        case .loading:
            loadingView
        case let .success(successData):
            if successData.data.count == 0 {
                emptyView()
            } else {
                podcastsListView(podcastsList: successData.data)
            }
        case let .error(errorState):
            errorView(errorState: errorState)
        }
    }

    private var loadingView: some View {
        VStack {
            ProgressView("Loading Spaces...")
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @ViewBuilder
    private func podcastsListView(podcastsList: NSArray) -> some View {
        List {
            ForEach(0 ..< podcastsList.count, id: \.self) { index in
                if let podcast = podcastsList[index] as? UiPodcast {
                    PodcastRowView(podcast: podcast, accountType: accountType)
                } else {
                    Text("Invalid item at index \\(index)")
                        .foregroundColor(.red)
                }
            }
        }
        .listStyle(.plain)
    }

    private func emptyView() -> some View {
        VStack(spacing: 16) {
            Image(systemName: "mic.slash.fill")
                .font(.largeTitle)
                .foregroundColor(.secondary)
            Text("No Spaces Available")
                .font(.headline)
            Text("There are currently no live or recorded Spaces to show.")
                .font(.subheadline)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func errorView(errorState: UiStateError<NSArray>) -> some View {
        let errorMessage = errorState.throwable.message ?? "An unknown error occurred"
        return VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.largeTitle)
                .foregroundColor(.red)
            Text("Error Loading Spaces")
                .font(.headline)
            Text(errorMessage)
                .font(.subheadline)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct PodcastRowView: View {
    let podcast: UiPodcast
    let accountType: AccountType
    @EnvironmentObject private var router: FlareRouter

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                if !podcast.ended {
                    LiveIndicatorView()
                }
                Spacer()
//                Image(systemName: "ellipsis")
//                    .foregroundColor(.secondary)
            }

            Text(podcast.title)
                .font(.title3.weight(.semibold))
                .lineLimit(3)

//            HStack(spacing: 4) {
//                Image(systemName: "headphones")
//                Text("\(podcast.listeners.count) Listening")
//            }
//            .font(.subheadline)
//            .foregroundColor(.secondary)
//            .padding(.bottom, 4)

            HStack(spacing: 8) {
                KFImage(URL(string: podcast.creator.avatar))
                    .resizable()
                    .placeholder { Image(systemName: "person.circle.fill").resizable().foregroundColor(.gray) }
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 24, height: 24)
                    .clipShape(Circle())

                Text(podcast.creator.name.raw)
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
            }
        }
        .padding()
        .background(Color.teal.opacity(0.1))
        .cornerRadius(12)
        .contentShape(Rectangle())
        .onTapGesture {
            router.navigate(to: .podcastSheet(accountType: accountType, podcastId: podcast.id))
        }
        .listRowSeparator(.hidden)
        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
    }
}

private struct LiveIndicatorView: View {
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "antenna.radiowaves.left.and.right")
            Text("Live")
        }
        .font(.caption.weight(.bold))
        .foregroundColor(.red)
        .padding(.horizontal, 6)
        .padding(.vertical, 3)
        .background(Color.red.opacity(0.1))
        .cornerRadius(6)
    }
}

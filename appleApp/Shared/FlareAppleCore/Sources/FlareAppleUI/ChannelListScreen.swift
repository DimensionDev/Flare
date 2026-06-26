import SwiftUI
@preconcurrency import KotlinSharedUI
import FlareAppleCore

public struct ChannelListScreen<Destination: Hashable>: View {
    @StateObject private var presenter: KotlinPresenter<MisskeyChannelListPresenterState>
    private let accountType: AccountType
    private let timelineDestination: (UiTimelineTabItem) -> Destination
    @State private var selectedTab: MisskeyChannelListPresenterStateType = .following

    public init(
        accountType: AccountType,
        timelineDestination: @escaping (UiTimelineTabItem) -> Destination
    ) {
        self.accountType = accountType
        self.timelineDestination = timelineDestination
        self._presenter = .init(wrappedValue: .init(presenter: MisskeyChannelListPresenter(accountType: accountType)))
    }

    public var body: some View {
        List {
            PagingView(data: presenter.state.data) { item in
                 NavigationLink(value: timelineDestination(presenter.state.timelineTabItem(item: item))) {
                     UiListView(data: item)
                         .contextMenu {
                             if case .channel(let data) = onEnum(of: item) {
                                 if let isFollowing = data.isFollowing {
                                     if isFollowing.boolValue {
                                         Button {
                                             presenter.state.unfollow(list: item)
                                         } label: {
                                             Label {
                                                 Text("misskey_channel_unfollow", bundle: FlareAppleUILocalization.bundle)
                                             } icon: {
                                                 Image(fontAwesome: .minus)
                                             }
                                         }
                                     } else {
                                         Button {
                                             presenter.state.follow(list: item)
                                         } label: {
                                             Label {
                                                 Text("misskey_channel_follow", bundle: FlareAppleUILocalization.bundle)
                                             } icon: {
                                                 Image(fontAwesome: .plus)
                                             }
                                         }
                                     }
                                 }
                                 if let isFavorited = data.isFavorited {
                                     if isFavorited.boolValue {
                                         Button {
                                             presenter.state.unfavorite(list: item)
                                         } label: {
                                             Label {
                                                 Text("misskey_channel_unfavorite", bundle: FlareAppleUILocalization.bundle)
                                             } icon: {
                                                 Image(fontAwesome: .heartCircleMinus)
                                             }
                                         }
                                     } else {
                                         Button {
                                             presenter.state.favorite(list: item)
                                         } label: {
                                             Label {
                                                 Text("Favourite", bundle: FlareAppleUILocalization.bundle)
                                             } icon: {
                                                 Image(fontAwesome: .heartCirclePlus)
                                             }
                                         }
                                     }
                                 }
                             }
                         }
                 }
             } loadingContent: {
                 UiListPlaceholder()
             }
        }
        .safeAreaInset(edge: .top, content: {
            Picker(selection: $selectedTab) {
                ForEach(presenter.state.allTypes, id: \.self) { tab in
                    Text(tab.localizedName).tag(tab)
                }
            } label: {
                Text("Tabs", bundle: FlareAppleUILocalization.bundle)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
        })
        .refreshable {
             try? await presenter.state.refreshSuspend()
        }
        .onChange(of: selectedTab, { oldValue, newValue in
            presenter.state.setType(data: newValue)
        })
        .navigationTitle(Text("channels_title", bundle: FlareAppleUILocalization.bundle))
    }
}

extension MisskeyChannelListPresenterStateType {
    var localizedName: String {
        switch self {
        case .following:
            return FlareAppleUILocalization.string("matrix_following")
        case .favorites:
            return FlareAppleUILocalization.string("home_tab_favorite_title")
        case .owned:
            return FlareAppleUILocalization.string("misskey_channel_tab_owned")
        case .featured:
            return FlareAppleUILocalization.string("home_tab_featured_title")
        }
    }
}

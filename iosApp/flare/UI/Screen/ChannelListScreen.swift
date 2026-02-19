import SwiftUI
@preconcurrency import KotlinSharedUI

struct ChannelListScreen: View {
    @StateObject private var presenter: KotlinPresenter<MisskeyChannelListPresenterState>
    let accountType: AccountType
    @State private var selectedTab: MisskeyChannelListPresenterStateType = .following
    init(accountType: AccountType) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: MisskeyChannelListPresenter(accountType: accountType)))
    }

    var body: some View {
        List {
            PagingView(data: presenter.state.data) { item in
                 NavigationLink(value: Route.timeline(
                     Misskey.ChannelTimelineTabItem(
                         channelId: item.id,
                         account: accountType,
                         metaData: TabMetaData(
                             title: TitleType.Text(content: item.title),
                             icon: IconType.Material(icon: .list)
                         )
                     )
                 )) {
                     UiListView(data: item)
                         .contextMenu {
                             if case .channel(let data) = onEnum(of: item) {
                                 if let isFollowing = data.isFollowing {
                                     if isFollowing.boolValue {
                                         Button {
                                             presenter.state.unfollow(list: item)
                                         } label: {
                                             Label {
                                                 Text("misskey_channel_unfollow")
                                             } icon: {
                                                 Image(.faMinus)
                                             }
                                         }
                                     } else {
                                         Button {
                                             presenter.state.follow(list: item)
                                         } label: {
                                             Label {
                                                 Text("misskey_channel_follow")
                                             } icon: {
                                                 Image(.faPlus)
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
                                                 Text("misskey_channel_unfavorite")
                                             } icon: {
                                                 Image(.faHeartCircleMinus)
                                             }
                                         }
                                     } else {
                                         Button {
                                             presenter.state.favorite(list: item)
                                         } label: {
                                             Label {
                                                 Text("misskey_channel_favorite")
                                             } icon: {
                                                 Image(.faHeartCirclePlus )
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
            Picker("Tabs", selection: $selectedTab) {
                ForEach(presenter.state.allTypes, id: \.self) { tab in
                    Text(tab.localizedName).tag(tab)
                }
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
        .navigationTitle("channels_title")
    }
}

extension MisskeyChannelListPresenterStateType {
    var localizedName: String {
        switch self {
        case .following: return NSLocalizedString("misskey_channel_tab_following", comment: "")
        case .favorites: return NSLocalizedString("misskey_channel_tab_favorites", comment: "")
        case .owned: return NSLocalizedString("misskey_channel_tab_owned", comment: "")
        case .featured: return NSLocalizedString("misskey_channel_tab_featured", comment: "")
        }
    }
}

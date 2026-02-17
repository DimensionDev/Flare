import SwiftUI
@preconcurrency import KotlinSharedUI

struct ChannelListScreen: View {
    let accountType: AccountType
    @State private var selectedTab: ChannelTab = .following

    var body: some View {
        VStack {
            Picker("Tabs", selection: $selectedTab) {
                ForEach(ChannelTab.allCases, id: \.self) { tab in
                    Text(tab.localizedName).tag(tab)
                }
            }
            .pickerStyle(.segmented)
            .padding()

            switch selectedTab {
            case .following:
                ChannelListOfType(accountType: accountType, type: .following)
            case .favorites:
                ChannelListOfType(accountType: accountType, type: .favorites)
            case .owned:
                ChannelListOfType(accountType: accountType, type: .owned)
            case .featured:
                ChannelListOfType(accountType: accountType, type: .featured)
            }
        }
        .navigationTitle(NSLocalizedString("channels_title", comment: ""))
    }
}

enum ChannelTab: CaseIterable {
    case following, favorites, owned, featured

    var localizedName: String {
        switch self {
        case .following: return NSLocalizedString("misskey_channel_tab_following", comment: "")
        case .favorites: return NSLocalizedString("misskey_channel_tab_favorites", comment: "")
        case .owned: return NSLocalizedString("misskey_channel_tab_owned", comment: "")
        case .featured: return NSLocalizedString("misskey_channel_tab_featured", comment: "")
        }
    }
}

struct ChannelListOfType: View {
    let accountType: AccountType
    @StateObject private var presenter: KotlinPresenter<MisskeyBaseChannelPresenterState>

    init(accountType: AccountType, type: ChannelTab) {
        self.accountType = accountType
        let p: MisskeyBaseChannelPresenter
        switch type {
        case .following: p = MisskeyFollowedChannelsPresenter(accountType: accountType)
        case .favorites: p = MisskeyFavoriteChannelsPresenter(accountType: accountType)
        case .owned: p = MisskeyOwnedChannelsPresenter(accountType: accountType)
        case .featured: p = MisskeyFeaturedChannelsPresenter(accountType: accountType)
        }
        _presenter = StateObject(wrappedValue: KotlinPresenter(p))
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
                 }
             } loadingContent: {
                 UiListPlaceholder()
             }
        }
        .refreshable {
             try? await presenter.state.refreshSuspend()
        }
    }
}

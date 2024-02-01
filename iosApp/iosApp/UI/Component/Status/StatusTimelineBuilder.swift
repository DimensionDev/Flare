import SwiftUI
import shared

struct StatusTimelineComponent: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let data: UiState<LazyPagingItemsProxy<UiStatus>>
    let mastodonEvent: MastodonStatusEvent
    let misskeyEvent: MisskeyStatusEvent
    let blueskyEvent: BlueskyStatusEvent
    let xqtEvent: XQTStatusEvent
    var body: some View {
        switch onEnum(of: data) {
        case .success(let success):
            if (success.data.loadState.refresh is Paging_commonLoadState.Loading ||
                success.data.loadState.prepend is Paging_commonLoadState.Loading) &&
                success.data.itemCount == 0 {
                ForEach(0...10, id: \.self) { _ in
                    StatusPlaceHolder()
                        .if(horizontalSizeClass != .compact) { view in
                            view.padding([.horizontal])
                        }
                }
            } else if (success.data.loadState.refresh is Paging_commonLoadState.Error ||
                       success.data.loadState.prepend is Paging_commonLoadState.Error) &&
                        success.data.itemCount == 0 {
                Text("timeline_load_error", comment: "Timeline loading error")
            } else if success.data.itemCount == 0 {
                Text("timeline_load_empty", comment: "Timeline is empty")
            } else {
                StatusTimeline(
                    pagingSource: success.data,
                    mastodonEvent: mastodonEvent,
                    misskeyEvent: misskeyEvent,
                    blueskyEvent: blueskyEvent,
                    xqtEvent: xqtEvent
                )
            }
        case .error(let error):
            Text("timeline_load_error", comment: "Timeline loading error")
        case .loading:
            ForEach(0...10, id: \.self) { _ in
                StatusPlaceHolder()
                    .if(horizontalSizeClass != .compact) { view in
                        view.padding([.horizontal])
                    }
            }
        }
    }
}

struct StatusTimeline: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let pagingSource: LazyPagingItemsProxy<UiStatus>
    let mastodonEvent: MastodonStatusEvent
    let misskeyEvent: MisskeyStatusEvent
    let blueskyEvent: BlueskyStatusEvent
    let xqtEvent: XQTStatusEvent
    var body: some View {
        if (pagingSource.loadState.refresh is Paging_commonLoadState.Loading ||
            pagingSource.loadState.prepend is Paging_commonLoadState.Loading) &&
            pagingSource.itemCount == 0 {
            ForEach(0...10, id: \.self) { _ in
                StatusPlaceHolder()
                    .if(horizontalSizeClass != .compact) { view in
                        view.padding([.horizontal])
                    }
            }
        } else if (pagingSource.loadState.refresh is Paging_commonLoadState.Error ||
                   pagingSource.loadState.prepend is Paging_commonLoadState.Error) &&
                    pagingSource.itemCount == 0 {
            Text("timeline_load_error", comment: "Timeline loading error")
        } else if pagingSource.itemCount == 0 {
            Text("timeline_load_empty", comment: "Timeline is empty")
        } else {
            ForEach(1...pagingSource.itemCount, id: \.self) { index in
                let data = pagingSource.peek(index: index - 1)
                VStack {
                    if let status = data {
                        StatusItemView(
                            status: status,
                            mastodonEvent: mastodonEvent,
                            misskeyEvent: misskeyEvent,
                            blueskyEvent: blueskyEvent,
                            xqtEvent: xqtEvent
                        )
                        .onTapGesture {
                            openURL(URL(string: AppDeepLink.StatusDetail.shared.invoke(statusKey: status.statusKey))!)
                        }
                    } else {
                        StatusPlaceHolder()
                    }
                }
                .onAppear {
                    pagingSource.get(index: index - 1)
                }
                .if(horizontalSizeClass != .compact) { view in
                    view.padding([.horizontal])
                }
            }
        }
    }
}

struct StatusItemView: View {
    let status: UiStatus
    let mastodonEvent: MastodonStatusEvent
    let misskeyEvent: MisskeyStatusEvent
    let blueskyEvent: BlueskyStatusEvent
    let xqtEvent: XQTStatusEvent
    var body: some View {
        switch onEnum(of: status) {
        case .mastodon(let mastodon):
            MastodonStatusComponent(mastodon: mastodon, event: mastodonEvent)
        case .mastodonNotification(let mastodonNotification):
            MastodonNotificationComponent(data: mastodonNotification, event: mastodonEvent)
        case .misskey(let misskey):
            MisskeyStatusComponent(misskey: misskey, event: misskeyEvent)
        case .misskeyNotification(let misskeyNotification):
            MisskeyNotificationComponent(data: misskeyNotification, event: misskeyEvent)
        case .bluesky(let bluesky):
            BlueskyStatusComponent(bluesky: bluesky, event: blueskyEvent)
        case .blueskyNotification(let blueskyNotification):
            BlueskyNotificationComponent(data: blueskyNotification)
        case .xQT(let xqt):
            XQTStatusComponent(xqt: xqt, event: xqtEvent)
        }
    }
}

struct StatusPlaceHolder: View {
    var body: some View {
        CommonStatusComponent(
            content: "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed non risus. Suspendisse lectus tortor, dignissim sit amet, adipiscing nec, ultricies sed, dolor.",
            contentWarning: nil,
            user: UiUser.Bluesky(
                userKey: MicroBlogKey(id: "", host: ""),
                displayName: "hahaname",
                handleInternal: "haha.haha",
                avatarUrl: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg",
                bannerUrl: nil,
                description: nil,
                matrices: UiUser.BlueskyMatrices(fansCount: 0, followsCount: 0, statusesCount: 0),
                relation: UiRelationBluesky(isFans: false, following: false, blocking: false, muting: false),
                accountHost: ""
            ),
            medias: [],
            timestamp: 1696838289,
            headerTrailing: {EmptyView()},
            onMediaClick: { _ in },
            sensitive: false,
            card: nil
        )
        .redacted(reason: .placeholder)
    }
}

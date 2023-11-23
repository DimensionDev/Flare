import SwiftUI
import shared

struct StatusTimelineComponent: View {
    let data: UiState<LazyPagingItemsProxy<UiStatus>>
    var body: some View {
        switch onEnum(of: data) {
        case .success(let success):
            if (success.data.loadState.refresh is Paging_commonLoadState.Loading || success.data.loadState.prepend is Paging_commonLoadState.Loading) && success.data.itemCount == 0 {
                ForEach(0...10, id: \.self) { _ in
                    StatusPlaceHolder()
                }
            } else if (success.data.loadState.refresh is Paging_commonLoadState.Error || success.data.loadState.prepend is Paging_commonLoadState.Error) && success.data.itemCount == 0 {
                Text("error: ")
            } else if (success.data.itemCount == 0) {
                Text("Empty list")
            } else  {
                StatusTimeline(pagingSource: success.data)
            }
        case .error(let error):
            Text("error: " + (error.throwable.message ?? ""))
        case .loading(_):
            ForEach(0...10, id: \.self) { _ in
                StatusPlaceHolder()
            }
        }
    }
}

struct StatusTimeline: View {
    let pagingSource: LazyPagingItemsProxy<UiStatus>
    var body: some View {
        if (pagingSource.loadState.refresh is Paging_commonLoadState.Loading || pagingSource.loadState.prepend is Paging_commonLoadState.Loading) && pagingSource.itemCount == 0 {
            ForEach(0...10, id: \.self) { _ in
                StatusPlaceHolder()
            }
        } else if (pagingSource.loadState.refresh is Paging_commonLoadState.Error || pagingSource.loadState.prepend is Paging_commonLoadState.Error) && pagingSource.itemCount == 0 {
            Text("error: ")
        } else if (pagingSource.itemCount == 0) {
            Text("Empty list")
        } else  {
            ForEach(1...pagingSource.itemCount, id: \.self) { index in
                let data = pagingSource.peek(index: index - 1)
                VStack {
                    if (data != nil) {
                        switch onEnum(of: data!) {
                        case .mastodon(let mastodon):
                            MastodonStatusComponent(mastodon: mastodon)
                        case .mastodonNotification(let mastodonNotification):
                            MastodonNotificationComponent(data: mastodonNotification)
                        case .misskey(let misskey):
                            MisskeyStatusComponent(misskey: misskey)
                        case .misskeyNotification(let misskeyNotification):
                            MisskeyNotificationComponent(data: misskeyNotification)
                        case .bluesky(let bluesky):
                            BlueskyStatusComponent(bluesky: bluesky)
                        case .blueskyNotification(let blueskyNotification):
                            BlueskyNotificationComponent(data: blueskyNotification)
                        }
                    } else {
                        StatusPlaceHolder()
                    }
                }.onAppear {
                    pagingSource.get(index: index - 1)
                }
            }
        }
    }
}

struct StatusPlaceHolder: View {
    var body: some View {
        CommonStatusComponent(content: "haha",user: UiUser.Bluesky(userKey: MicroBlogKey(id: "", host: ""), displayName: "hahaname", handleInternal: "haha.haha", avatarUrl: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg", bannerUrl: nil, description: nil, matrices: UiUser.BlueskyMatrices(fansCount: 0, followsCount: 0, statusesCount: 0), relation: UiRelationBluesky(isFans: false, following: false, blocking: false, muting: false), accountHost: ""), medias: [], timestamp: 1696838289, headerTrailing: {EmptyView()})
            .redacted(reason: .placeholder)
    }
}

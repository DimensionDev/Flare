import SwiftUI
import shared

@ViewBuilder
func StatusTimelineBuilder(data: Paging_compose_commonLazyPagingItems<UiStatus>) -> some View {
    if data.itemCount > 0 {
        ForEach(StatusCollection(data: data), id: \.?.itemKey) { item in
            VStack {
                if (item != nil) {
                    switch onEnum(of: item!) {
                    case .mastodon(let mastodon):
                        MastodonStatusComponent(mastodon: mastodon)
                    case .mastodonNotification(let mastodonNotification):
                        MastodonNotificationComponent(data: mastodonNotification)
                    case .misskey(let misskey):
                        MisskeyStatusComponent(misskey: misskey)
                    case .misskeyNotification(_):
                        VStack {
                            
                        }
                    }
                } else {
                    CommonStatusComponent(content: "haha", avatar: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg", name: "hahaname", handle: "haha.haha", userKey: MicroBlogKey(id: "", host: ""), medias: [], timestamp: 1696838289, headerTrailing: {EmptyView()})
                        .redacted(reason: .placeholder)
                }
            }
        }
    } else {
        ForEach(1...10, id: \.self) { _ in
            CommonStatusComponent(content: "haha", avatar: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg", name: "hahaname", handle: "haha.haha", userKey: MicroBlogKey(id: "", host: ""), medias: [], timestamp: 1696838289, headerTrailing: {EmptyView()})
                .redacted(reason: .placeholder)
        }
    }
}

struct StatusCollection: RandomAccessCollection {
    var data: Paging_compose_commonLazyPagingItems<UiStatus>
    
    var startIndex: Int { 0 }
    var endIndex: Int { Int(data.itemCount) }
    
    subscript(position: Int) -> UiStatus? {
        return data.peek(index: Int32(position))
    }
}


@ViewBuilder
func StatusTimelineStateBuilder(data: UiState<Paging_compose_commonLazyPagingItems<UiStatus>>) -> some View {
    switch onEnum(of: data) {
    case .success(let data):
        StatusTimelineBuilder(data: data.data)
    case .error(let error):
        Text("error: " + (error.throwable.message ?? ""))
    case .loading(_):
        ProgressView()
    }
}

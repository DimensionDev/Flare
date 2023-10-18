import SwiftUI
import shared

@ViewBuilder
func StatusTimelineBuilder(data: Paging_compose_commonLazyPagingItems<UiStatus>) -> some View {
    if data.itemCount > 0 {
        ForEach(1...data.itemCount, id: \.self) { index in
            let item = data.peek(index: index - 1)
            VStack {
                if (item != nil) {
                    switch onEnum(of: item!) {
                    case .mastodon(let mastodon):
                        MastodonStatusComponent(mastodon: mastodon)
                    case .mastodonNotification(let mastodonNotification):
                        MastodonNotificationComponent(data: mastodonNotification)
                    case .misskey(let misskey):
                        MisskeyStatusComponent(misskey: misskey)
                    case .misskeyNotification(let misskeyNotification):
                        MisskeyNotificationComponent(data: misskeyNotification)
                    }
                } else {
                    CommonStatusComponent(content: "haha", avatar: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg", name: "hahaname", handle: "haha.haha", userKey: MicroBlogKey(id: "", host: ""), medias: [], timestamp: 1696838289, headerTrailing: {EmptyView()})
                        .redacted(reason: .placeholder)
                }
            }.onAppear {
                data.get(index: index - 1)
            }
        }
    } else {
        Text("Empty list")
    }
}

struct StatusCollection: RandomAccessCollection {
    var data: Paging_compose_commonLazyPagingItems<UiStatus>
    
    var startIndex: Int { 0 }
    var endIndex: Int { Int(data.itemCount - 1) }
    
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

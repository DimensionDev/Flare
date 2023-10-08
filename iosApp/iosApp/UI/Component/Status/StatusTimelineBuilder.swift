import SwiftUI
import shared

@ViewBuilder
func StatusTimelineBuilder(data: Paging_compose_commonLazyPagingItems<UiStatus>) -> some View {
    if data.itemCount > 0 {
        ForEach(1...(data.itemCount), id: \.self) { index in
            VStack {
                let item = data.peek(index: index - 1)
                if (item != nil) {
                    switch onEnum(of: item!) {
                    case .mastodon(let mastodon):
                        MastodonStatusComponent(content: mastodon.extra.contentMarkdown, avatar: mastodon.user.avatarUrl, name: mastodon.user.extra.nameMarkdown, handle: mastodon.user.handle, userKey: mastodon.user.userKey)
                    case .mastodonNotification(_):
                        VStack {
                            
                        }
                    case .misskey(let misskey):
                        MastodonStatusComponent(content: misskey.extra.contentMarkdown, avatar: misskey.user.avatarUrl, name: misskey.user.extra.nameMarkdown, handle: misskey.user.handle, userKey: misskey.user.userKey)
                    case .misskeyNotification(_):
                        VStack {
                            
                        }
                    }
                } else {
                    // TODO: Placeholder
                }
            }.onAppear {
                data.get(index: index - 1)
            }
        }
    } else {
        VStack {
            // TODO: Placeholder
        }
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

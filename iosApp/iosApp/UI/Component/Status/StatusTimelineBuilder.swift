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
                        MastodonStatusComponent(mastodon: mastodon)
                    case .mastodonNotification(_):
                        VStack {
                            
                        }
                    case .misskey(let misskey):
                        MisskeyStatusComponent(misskey: misskey)
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

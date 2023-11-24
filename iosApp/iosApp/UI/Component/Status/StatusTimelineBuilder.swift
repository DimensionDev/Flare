import SwiftUI
import shared

struct StatusTimelineComponent: View {
    let data: UiState<LazyPagingItemsProxy<UiStatus>>
    let mastodonEvent: MastodonStatusEvent
    let misskeyEvent: MisskeyStatusEvent
    let blueskyEvent: BlueskyStatusEvent
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
                StatusTimeline(pagingSource: success.data, mastodonEvent: mastodonEvent, misskeyEvent: misskeyEvent, blueskyEvent: blueskyEvent)
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
    let mastodonEvent: MastodonStatusEvent
    let misskeyEvent: MisskeyStatusEvent
    let blueskyEvent: BlueskyStatusEvent
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
        CommonStatusComponent(content: "haha",user: UiUser.Bluesky(userKey: MicroBlogKey(id: "", host: ""), displayName: "hahaname", handleInternal: "haha.haha", avatarUrl: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg", bannerUrl: nil, description: nil, matrices: UiUser.BlueskyMatrices(fansCount: 0, followsCount: 0, statusesCount: 0), relation: UiRelationBluesky(isFans: false, following: false, blocking: false, muting: false), accountHost: ""), medias: [], timestamp: 1696838289, headerTrailing: {EmptyView()}, onMediaClick: { _ in })
            .redacted(reason: .placeholder)
    }
}

@Observable
class StatusEvent : MastodonStatusEvent, MisskeyStatusEvent, BlueskyStatusEvent {
    let accountRepository = KoinHelper.shared.accountRepository

    func onReplyClick(status: UiStatus.Mastodon) {
    }

    func onReblogClick(status: UiStatus.Mastodon) {
        Task {
            if let account = accountRepository.get(accountKey: status.accountKey) as? UiAccountMastodon {
                try? await account.dataSource.reblog(status: status)
            }
        }
    }

    func onLikeClick(status: UiStatus.Mastodon) {
        Task {
            if let account = accountRepository.get(accountKey: status.accountKey) as? UiAccountMastodon {
                try? await account.dataSource.like(status: status)
            }
        }
    }

    func onBookmarkClick(status: UiStatus.Mastodon) {
        Task {
            if let account = accountRepository.get(accountKey: status.accountKey) as? UiAccountMastodon {
                try? await account.dataSource.bookmark(status: status)
            }
        }
    }

    func onMediaClick(media: UiMedia) {
    }

    func onDeleteClick(status: UiStatus.Mastodon) {
    }

    func onReportClick(status: UiStatus.Mastodon) {
    }

    func onReactionClick(data: UiStatus.Misskey, reaction: UiStatus.MisskeyEmojiReaction) {
        Task {
            if let account = accountRepository.get(accountKey: data.accountKey) as? UiAccountMisskey {
                try? await account.dataSource.react(status: data, reaction: reaction.name)
            }
        }
    }

    func onReplyClick(data: UiStatus.Misskey) {
    }

    func onReblogClick(data: UiStatus.Misskey) {
        Task {
            if let account = accountRepository.get(accountKey: data.accountKey) as? UiAccountMisskey {
                try? await account.dataSource.renote(status: data)
            }
        }
    }

    func onQuoteClick(data: UiStatus.Misskey) {
    }

    func onAddReactionClick(data: UiStatus.Misskey) {
    }

    func onDeleteClick(data: UiStatus.Misskey) {
    }

    func onReportClick(data: UiStatus.Misskey) {
    }

    func onReplyClick(data: UiStatus.Bluesky) {
    }

    func onReblogClick(data: UiStatus.Bluesky) {
        Task {
            if let account = accountRepository.get(accountKey: data.accountKey) as? UiAccountBluesky {
                try? await account.dataSource.reblog(data: data)
            }
        }
    }

    func onQuoteClick(data: UiStatus.Bluesky) {
    }

    func onLikeClick(data: UiStatus.Bluesky) {
        Task {
            if let account = accountRepository.get(accountKey: data.accountKey) as? UiAccountBluesky {
                try? await account.dataSource.like(data: data)
            }
        }
    }

    func onReportClick(data: UiStatus.Bluesky) {
    }

    func onDeleteClick(data: UiStatus.Bluesky) {
    }
}

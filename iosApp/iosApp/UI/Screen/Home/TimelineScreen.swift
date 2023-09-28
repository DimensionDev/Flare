import SwiftUI
import shared
import RichText

struct TimelineScreen: View {
    @State var viewModel = TimelineViewModel()
    var body: some View {
        List {
            switch viewModel.model.listState {
            case let success as UiStateSuccess<Paging_compose_commonLazyPagingItems<UiStatus>>:
                if success.data != nil && success.data?.itemCount ?? 0 > 0 {
                    ForEach(1...(success.data?.itemCount ?? 1), id: \.self) { index in
                        let item = success.data?.peek(index: index - 1)
                        switch item {
                        case let mastodon as UiStatus.Mastodon:
                            VStack {
                                MastodonStatusComponent(content: mastodon.content, avatar: mastodon.user.avatarUrl, name: mastodon.user.name, handle: mastodon.user.handle)
                            }.onAppear {
                                _ = success.data?.get(index: index - 1)
                            }
                        case let misskey as UiStatus.Misskey:
                            VStack {
                                MastodonStatusComponent(content: misskey.content, avatar: misskey.user.avatarUrl, name: misskey.user.name, handle: misskey.user.handle)
                            }.onAppear {
                                _ = success.data?.get(index: index - 1)
                            }
                        default:
                            Text("Unknown").onAppear {
                                _ = success.data?.get(index: index - 1)
                            }
                        }
                    }
                }
            case let error as UiStateError<Paging_compose_commonLazyPagingItems<UiStatus>>:
                Text("error: " + (error.throwable.message ?? ""))
            case _ as UiStateLoading<Paging_compose_commonLazyPagingItems<UiStatus>>:
                ProgressView()
            default:
                Text("unknown")
            }
        }.listStyle(.plain).refreshable {
            viewModel.model.refresh()
        }.activateViewModel(viewModel: viewModel)
    }
}

@Observable
class TimelineViewModel: MoleculeViewModelBase<HomeTimelineState, HomeTimelinePresenter> {
}

#Preview {
    TimelineScreen()
}


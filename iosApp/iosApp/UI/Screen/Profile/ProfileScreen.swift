import SwiftUI
import OrderedCollections
import shared
import MarkdownUI

struct ProfileScreen: View {
    let toProfileMedia: (MicroBlogKey) -> Void
    @State var viewModel: ProfileViewModel
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    init(userKey: MicroBlogKey?, toProfileMedia: @escaping (MicroBlogKey) -> Void) {
        self.toProfileMedia = toProfileMedia
        _viewModel = State(initialValue: ProfileViewModel(userKey: userKey))
    }
    var sideProfileHeader: some View {
        ScrollView {
            VStack {
                ProfileHeader(
                    user: viewModel.model.userState,
                    relation: viewModel.model.relationState,
                    isMe: viewModel.model.isMe,
                    onFollowClick: { user, relation in
                        viewModel.model.follow(user: user, data: relation)
                    }
                )
                if case .success(let userState) = onEnum(of: viewModel.model.userState) {
                    Button(action: {
                        toProfileMedia(userState.data.userKey)
                    }, label: {
                        LargeProfileImagePreviews(state: viewModel.model.mediaState)
                    })
                    .buttonStyle(.borderless)
                }
            }
        }
#if !os(macOS)
        .frame(width: 384)
#endif
    }
    var profileListContent: some View {
        List {
            if horizontalSizeClass == .compact {
                ProfileHeader(
                    user: viewModel.model.userState,
                    relation: viewModel.model.relationState,
                    isMe: viewModel.model.isMe,
                    onFollowClick: { user, relation in
                        viewModel.model.follow(user: user, data: relation)
                    }
                )
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets())
                if case .success(let userState) = onEnum(of: viewModel.model.userState) {
                    Button(action: {
                        toProfileMedia(userState.data.userKey)
                    }, label: {
                        SmallProfileMediaPreviews(state: viewModel.model.mediaState)
                    })
                    .buttonStyle(.borderless)
                    .listRowInsets(.none)
                }
            }
            StatusTimelineComponent(
                data: viewModel.model.listState,
                mastodonEvent: statusEvent,
                misskeyEvent: statusEvent,
                blueskyEvent: statusEvent,
                xqtEvent: statusEvent
            )
        }
        .refreshable {
            try? await viewModel.model.refresh()
        }
        .listStyle(.plain)
    }
    var body: some View {
        let title: LocalizedStringKey = if case .success(let user) = onEnum(of: viewModel.model.userState) {
            LocalizedStringKey(user.data.extra.nameMarkdown)
        } else {
            "Profile"
        }
        ZStack {
#if os(macOS)
            HSplitView {
                if horizontalSizeClass != .compact {
                    sideProfileHeader
                }
                profileListContent
            }
#else
            HStack {
                if horizontalSizeClass != .compact {
                    sideProfileHeader
                }
                profileListContent
            }
#endif
        }
#if !os(macOS)
        .if(horizontalSizeClass == .compact, transform: { view in
            view
                .ignoresSafeArea(edges: .top)
        })
#endif
        .if(horizontalSizeClass != .compact, transform: { view in
            view
#if !os(macOS)
                .navigationBarTitleDisplayMode(.inline)
#endif
                .navigationTitle(title)
        })
        .toolbar {
            Menu {
                if case .success(let user) = onEnum(of: viewModel.model.userState) {
                    if case .success(let isMe) = onEnum(of: viewModel.model.isMe), !isMe.data.boolValue {
                        if case .success(let relation) = onEnum(of: viewModel.model.relationState) {
                            switch onEnum(of: relation.data) {
                            case .bluesky(let blueskyRelation):
                                BlueskyMenu(
                                    relation: blueskyRelation,
                                    onMuteClick: { viewModel.model.mute(user: user.data, data: relation.data) },
                                    onBlockClick: { viewModel.model.block(user: user.data, data: relation.data) }
                                )
                            case .mastodon(let mastodonRelation):
                                MastodonMenu(
                                    relation: mastodonRelation,
                                    onMuteClick: { viewModel.model.mute(user: user.data, data: relation.data) },
                                    onBlockClick: { viewModel.model.block(user: user.data, data: relation.data) }
                                )
                            case .misskey(let misskeyRelation):
                                MisskeyMenu(
                                    relation: misskeyRelation,
                                    onMuteClick: { viewModel.model.mute(user: user.data, data: relation.data) },
                                    onBlockClick: { viewModel.model.block(user: user.data, data: relation.data) }
                                )
                            case .xQT(let xqtRelation):
                                XQTMenu(
                                    relation: xqtRelation,
                                    onMuteClick: { viewModel.model.mute(user: user.data, data: relation.data) },
                                    onBlockClick: { viewModel.model.block(user: user.data, data: relation.data) }
                                )
                            }
                        }
                        Button(action: { viewModel.model.report(user: user.data) }, label: {
                            Label("Report", systemImage: "exclamationmark.bubble")
                        })
                    }
                }
            } label: {
                Image(systemName: "ellipsis.circle")
            }
        }
        .activateViewModel(viewModel: viewModel)
    }
}

struct LargeProfileImagePreviews: View {
    let state: UiState<LazyPagingItemsProxy<UiMedia>>
    var body: some View {
        switch onEnum(of: state) {
        case .error:
            EmptyView()
        case .loading:
            EmptyView()
        case .success(let success):
            if success.data.isSuccess {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                    ForEach(0..<min(success.data.itemCount, 6), id: \.self) { index in
                        let item = success.data.peek(index: index)
                        if let media = item {
                            let image = media as? UiMediaImage
                            let shouldBlur = image?.sensitive ?? false
                            MediaItemComponent(media: media)
                                .if(shouldBlur, transform: { view in
                                    view.blur(radius: 32)
                                })
                                .onAppear(perform: {
                                    success.data.get(index: index)
                                })
                                .aspectRatio(1, contentMode: .fill)
                                .clipped()
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .padding(.horizontal)
            }
        }
    }
}

struct SmallProfileMediaPreviews: View {
    let state: UiState<LazyPagingItemsProxy<UiMedia>>
    var body: some View {
        switch onEnum(of: state) {
        case .error:
            EmptyView()
        case .loading:
            EmptyView()
        case .success(let success):
            if success.data.isSuccess {
                ScrollView(.horizontal) {
                    LazyHStack(content: {
                        ForEach(0..<min(success.data.itemCount, 6), id: \.self) { index in
                            let item = success.data.peek(index: index)
                            if let media = item {
                                let image = media as? UiMediaImage
                                let shouldBlur = image?.sensitive ?? false
                                MediaItemComponent(media: media)
                                    .if(shouldBlur, transform: { view in
                                        view.blur(radius: 32)
                                    })
                                    .onAppear(perform: {
                                        success.data.get(index: index)
                                    })
                                    .aspectRatio(1, contentMode: .fill)
                                    .clipped()
                                    .frame(width: 48, height: 48)
                            }
                        }
                    })
                }
            }
        }
    }
}

struct ProfileHeader: View {
    let user: UiState<UiUser>
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiUser, UiRelation) -> Void
    var body: some View {
        switch onEnum(of: user) {
        case .error:
            Text("error")
        case .loading:
            CommonProfileHeader(
                bannerUrl: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500",
                avatarUrl: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg",
                displayName: "test",
                handle: "test@test.test",
                description: "tefewfewfewfewfewst",
                headerTrailing: {
                    Text("header")
                }, handleTrailing: {
                    Text("handle")
                }, content: {
                    Text("content")
                }
            )
            .redacted(reason: .placeholder)
        case .success(let data):
            ProfileHeaderSuccess(
                user: data.data,
                relation: relation,
                isMe: isMe,
                onFollowClick: { relation in onFollowClick(data.data, relation) }
            )
        }
    }
}

struct ProfileHeaderSuccess: View {
    let user: UiUser
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    var body: some View {
        switch onEnum(of: user) {
        case .mastodon(let mastodon):
            MastodonProfileHeader(user: mastodon, relation: relation, isMe: isMe, onFollowClick: onFollowClick)
        case .misskey(let misskey):
            MisskeyProfileHeader(user: misskey, relation: relation, isMe: isMe, onFollowClick: onFollowClick)
        case .bluesky(let bluesky):
            BlueskyProfileHeader(user: bluesky, relation: relation, isMe: isMe, onFollowClick: onFollowClick)
        case .xQT(let xqt):
            XQTProfileHeader(user: xqt, relation: relation, isMe: isMe, onFollowClick: onFollowClick)
        }
    }
}

struct MatrixView: View {
    let followCount: String
    let fansCount: String
    var body: some View {
        HStack {
            Text(followCount)
            Text("following")
            Divider()
            Text(fansCount)
            Text("followers")
        }
        .font(.caption)
    }
}

struct FieldsView: View {
    let fields: ImmutableListWrapper<KotlinPair<NSString, NSString>>
    var body: some View {
        if fields.size > 0 {
            VStack(alignment: .leading) {
                ForEach(0..<fields.size, id: \.self) { index in
                    let key = fields.get(index: index).first as? String ?? ""
                    let value = fields.get(index: index).second as? String ?? ""
                    Text(key)
                        .font(.caption)
                    Markdown(value)
                        .font(.body)
                        .markdownInlineImageProvider(.emoji)
                    if index != fields.size - 1 {
                        Divider()
                    }
                }
                .padding(.horizontal)
            }
            .padding(.vertical)
#if !os(macOS)
            .background(Color(UIColor.secondarySystemBackground))
#else
            .background(Color(NSColor.windowBackgroundColor))
#endif
            .clipShape(RoundedRectangle(cornerRadius: 8))
        } else {
            EmptyView()
        }
    }
}
@Observable
class ProfileViewModel: MoleculeViewModelProto {
    let presenter: ProfilePresenter
    var model: ProfileState
    typealias Model = ProfileState
    typealias Presenter = ProfilePresenter
    init(userKey: MicroBlogKey?) {
        self.presenter = ProfilePresenter(userKey: userKey)
        self.model = presenter.models.value
    }
}

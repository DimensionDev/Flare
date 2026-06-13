import AppleFontAwesome
import FlareAppleCore
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import SwiftUI

struct ProfileScreen: View {
    let accountType: AccountType
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    let goBack: () -> Void

    @StateObject private var presenter: KotlinPresenter<ProfileState>
    @State private var selectedTab = 0

    init(
        accountType: AccountType,
        userKey: MicroBlogKey?,
        onFollowingClick: @escaping (MicroBlogKey) -> Void,
        onFansClick: @escaping (MicroBlogKey) -> Void,
        goBack: @escaping () -> Void = {}
    ) {
        self.accountType = accountType
        self.onFollowingClick = onFollowingClick
        self.onFansClick = onFansClick
        self.goBack = goBack
        _presenter = .init(
            wrappedValue: .init(
                presenter: ProfilePresenter(accountType: accountType, userKey: userKey)
            )
        )
    }

    var body: some View {
        compatBody
    }

    var compatBody: some View {
        ScrollView {
            LazyVStack(spacing: 12, pinnedViews: [.sectionHeaders]) {
                ProfileHeader(
                    user: presenter.state.userState,
                    relation: presenter.state.relationState,
                    followButtonState: presenter.state.followButtonState,
                    isMe: presenter.state.isMe,
                    onFollowClick: { user, followButtonState in
                        handleFollowAction(user: user, followButtonState: followButtonState)
                    },
                    onFollowingClick: onFollowingClick,
                    onFansClick: onFansClick
                )

                Section {
                    StateView(state: presenter.state.tabs) { tabsArray in
                        let tabs = tabsArray.cast(ProfileState.Tab.self)
                        profileTabsContent(tabs: tabs)
                    } loadingContent: {
                        ProgressView()
                            .padding()
                            .frame(maxWidth: .infinity)
                    }
                } header: {
                    profileTabPinnedHeader
                }
            }
            .padding(.bottom, 16)
        }
        .toolbar {
            ToolbarItem(placement: .principal) {
                switch onEnum(of: presenter.state.userState) {
                case .success(let user):
                    RichText(text: user.data.name)
                case .loading, .error:
                    Text(LocalizedStrings.string("profile_title", fallback: "Profile"))
                }
            }
            if !presenter.state.actions.isEmpty {
                ToolbarItemGroup(placement: .primaryAction) {
                    StatusActionsView(
                        data: presenter.state.actions,
                        useText: false,
                        allowSpacer: false
                    )
                }
            }
        }
    }

    @ViewBuilder
    private func profileTabsContent(tabs: [ProfileState.Tab]) -> some View {
        Group {
            if tabs.isEmpty {
                ListEmptyView()
                    .padding()
            } else {
                profileTabContent(tab: tabs[clampedSelectedTabIndex(for: tabs)])
            }
        }
        .onAppear {
            normalizeSelectedTab(tabCount: tabs.count)
        }
        .onChange(of: tabs.count) { _, count in
            normalizeSelectedTab(tabCount: count)
        }
    }

    @ViewBuilder
    private var profileTabPinnedHeader: some View {
        if case .success(let tabState) = onEnum(of: presenter.state.tabs) {
            let tabs = tabState.data.cast(ProfileState.Tab.self)
            if tabs.count > 1 {
                ProfileTabPicker(tabs: tabs, selectedTab: $selectedTab)
                    .pickerStyle(.segmented)
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity)
                    .background(.bar)
                    .overlay(alignment: .bottom) {
                        Divider()
                    }
            }
        }
    }

    @ViewBuilder
    private func profileTabContent(tab: ProfileState.Tab) -> some View {
        switch onEnum(of: tab) {
        case .timeline(let timeline):
            ProfileTimelineTabContent(presenter: timeline.presenter)
                .id(profileTimelineID(for: tab))
        case .media(let media):
            ProfileGalleryTabContent(
                presenter: media.presenter,
                accountType: accountType
            )
            .id(profileTimelineID(for: tab))
        }
    }

    private func clampedSelectedTabIndex(for tabs: [ProfileState.Tab]) -> Int {
        min(max(selectedTab, 0), max(tabs.count - 1, 0))
    }

    private func normalizeSelectedTab(tabCount: Int) {
        guard tabCount > 0 else {
            selectedTab = 0
            return
        }
        selectedTab = min(max(selectedTab, 0), tabCount - 1)
    }

    private func handleFollowAction(user: UiProfile, followButtonState: FollowButtonState) {
        switch onEnum(of: followButtonState) {
        case .blocked:
            presenter.state.unblock(userKey: user.key)
        case .following, .requested:
            presenter.state.unfollow(userKey: user.key)
        case .follow, .requestFollow:
            presenter.state.follow(userKey: user.key)
        }
    }
}

struct ProfileWithUserNameAndHostScreen: View {
    @StateObject private var presenter: KotlinPresenter<UserState>
    let accountType: AccountType
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    let goBack: () -> Void

    init(
        userName: String,
        host: String,
        accountType: AccountType,
        onFollowingClick: @escaping (MicroBlogKey) -> Void,
        onFansClick: @escaping (MicroBlogKey) -> Void,
        goBack: @escaping () -> Void = {}
    ) {
        self.accountType = accountType
        self.onFollowingClick = onFollowingClick
        self.onFansClick = onFansClick
        self.goBack = goBack
        _presenter = .init(
            wrappedValue: .init(
                presenter: ProfileWithUserNameAndHostPresenter(
                    userName: userName,
                    host: host,
                    accountType: accountType
                )
            )
        )
    }

    var body: some View {
        StateView(state: presenter.state.user) { user in
            ProfileScreen(
                accountType: accountType,
                userKey: user.key,
                onFollowingClick: onFollowingClick,
                onFansClick: onFansClick,
                goBack: goBack
            )
        } loadingContent: {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .navigationTitle(LocalizedStrings.string("profile_title", fallback: "Profile"))
    }
}

private struct ProfileTimelineTabContent: View {
    @StateObject private var presenter: KotlinPresenter<TimelineState>

    init(presenter: TimelinePresenter) {
        _presenter = .init(wrappedValue: .init(presenter: presenter))
    }

    var body: some View {
        TimelinePagingView(data: presenter.state.listState)
    }
}

private struct ProfileGalleryTabContent: View {
    private let accountType: AccountType
    private let columns = [
        GridItem(.adaptive(minimum: 140, maximum: 240), spacing: 8)
    ]

    @StateObject private var presenter: KotlinPresenter<ProfileMediaState>

    init(
        presenter: ProfileMediaPresenter,
        accountType: AccountType
    ) {
        self.accountType = accountType
        _presenter = .init(wrappedValue: .init(presenter: presenter))
    }

    var body: some View {
        content(data: presenter.state.mediaState)
    }

    @ViewBuilder
    private func content(data: PagingState<ProfileMedia>) -> some View {
        switch onEnum(of: data) {
        case .empty:
            ListEmptyView()
                .padding()
        case .error(let error):
            ListErrorView(error: error.error) {
                _ = error.onRetry()
            }
            .padding()
        case .loading:
            galleryGrid {
                ForEach(0..<12, id: \.self) { _ in
                    ProfileGalleryPlaceholder()
                }
            }
        case .success(let success):
            successContent(success)
        }
    }

    @ViewBuilder
    private func successContent(_ success: PagingStateSuccess<ProfileMedia>) -> some View {
        let count = Int(success.itemCount)
        if count == 0 {
            ListEmptyView()
                .padding()
        } else {
            galleryGrid {
                ForEach(0..<count, id: \.self) { index in
                    let kotlinIndex = Int32(index)
                    Group {
                        if let item = success.peek(index: kotlinIndex) {
                            ProfileGalleryTile(item: item, accountType: accountType)
                        } else {
                            ProfileGalleryPlaceholder()
                        }
                    }
                    .onAppear {
                        _ = success.get(index: kotlinIndex)
                    }
                }
            }
        }

        switch onEnum(of: success.appendState) {
        case .error(let error):
            ListErrorView(error: error.error) {
                success.retry()
            }
            .padding()
        case .loading:
            ProgressView()
                .padding()
                .frame(maxWidth: .infinity)
        case .notLoading:
            EmptyView()
        }
    }

    private func galleryGrid<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        LazyVGrid(columns: columns, spacing: 8) {
            content()
        }
        .padding(.horizontal)
    }
}

private struct ProfileGalleryTile: View {
    @Environment(\.openURL) private var openURL

    let item: ProfileMedia
    let accountType: AccountType

    var body: some View {
        Color.clear
            .aspectRatio(1, contentMode: .fit)
            .overlay {
                GeometryReader { proxy in
                    ZStack(alignment: .topTrailing) {
                        imageContent
                            .frame(width: proxy.size.width, height: proxy.size.height)
                            .clipped()

                        mediaBadge
                    }
                    .frame(width: proxy.size.width, height: proxy.size.height)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .stroke(Color.flareSeparator.opacity(0.6), lineWidth: 1)
            }
            .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .onTapGesture {
                openMedia()
            }
    }

    @ViewBuilder
    private var imageContent: some View {
        if let previewURL = item.media.profileGalleryPreviewURL {
            NetworkImage(
                data: previewURL,
                customHeader: item.media.profileGalleryCustomHeaders
            )
        } else {
            ProfileGalleryPlaceholder()
        }
    }

    @ViewBuilder
    private var mediaBadge: some View {
        switch item.media.profileGalleryMediaKind {
        case .image:
            EmptyView()
        case .video:
            Image(systemName: "play.fill")
                .profileGalleryBadgeStyle()
        case .gif:
            Text("GIF")
                .font(.caption2.bold())
                .profileGalleryBadgeStyle()
        case .audio:
            Image(systemName: "waveform")
                .profileGalleryBadgeStyle()
        }
    }

    private func openMedia() {
        let route = DeeplinkRoute.MediaStatusMedia(
            statusKey: item.statusKey,
            accountType: accountType,
            index: Int32(item.index),
            preview: item.media.profileGalleryRoutePreviewURL
        )
        if let url = URL(string: route.toUri()) {
            openURL(url)
        }
    }
}

private struct ProfileGalleryPlaceholder: View {
    var body: some View {
        Rectangle()
            .fill(Color.flareSecondarySystemBackground)
            .overlay {
                Image(systemName: "photo")
                    .foregroundStyle(.secondary)
            }
            .aspectRatio(1, contentMode: .fit)
            .redacted(reason: .placeholder)
    }
}

private enum ProfileGalleryMediaKind {
    case image
    case video
    case gif
    case audio
}

private extension View {
    func profileGalleryBadgeStyle() -> some View {
        self
            .foregroundStyle(.white)
            .padding(6)
            .background(.black.opacity(0.62), in: Capsule())
            .padding(6)
    }
}

private extension UiMedia {
    var profileGalleryMediaKind: ProfileGalleryMediaKind {
        switch onEnum(of: self) {
        case .image:
            .image
        case .video:
            .video
        case .gif:
            .gif
        case .audio:
            .audio
        }
    }

    var profileGalleryPreviewURL: String? {
        switch onEnum(of: self) {
        case .image(let image):
            image.previewUrl.isEmpty ? image.url : image.previewUrl
        case .video(let video):
            video.thumbnailUrl.isEmpty ? video.url : video.thumbnailUrl
        case .gif(let gif):
            gif.previewUrl.isEmpty ? gif.url : gif.previewUrl
        case .audio(let audio):
            audio.previewUrl
        }
    }

    var profileGalleryRoutePreviewURL: String? {
        switch onEnum(of: self) {
        case .image(let image):
            image.previewUrl
        case .video(let video):
            video.thumbnailUrl
        case .gif(let gif):
            gif.previewUrl
        case .audio:
            nil
        }
    }

    var profileGalleryCustomHeaders: [String: String]? {
        switch onEnum(of: self) {
        case .image(let image):
            image.customHeaders
        case .video(let video):
            video.customHeaders
        case .gif(let gif):
            gif.customHeaders
        case .audio(let audio):
            audio.customHeaders
        }
    }
}

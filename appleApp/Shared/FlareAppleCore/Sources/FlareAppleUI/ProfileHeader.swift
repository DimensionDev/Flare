import AppleFontAwesome
import FlareAppleCore
import KotlinSharedUI
import SwiftUI
import SwiftUIBackports

public enum CommonProfileHeaderConstants {
    public static let headerHeight: CGFloat = 200
    public static let avatarSize: CGFloat = 96
}

private extension FollowButtonState {
    var titleKey: LocalizedStringKey {
        switch onEnum(of: self) {
        case .blocked:
            "relation_blocked"
        case .following:
            "relation_following"
        case .requested:
            "relation_requested"
        case .requestFollow:
            "relation_request_follow"
        case .follow:
            "relation_follow"
        }
    }
}

public struct CommonProfileHeader: View {
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.openURL) private var openURL
    #if os(iOS)
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    #endif

    private let user: UiProfile
    private let relation: UiState<UiRelation>
    private let followButtonState: UiState<FollowButtonState>
    private let isMe: UiState<KotlinBoolean>
    private let onFollowClick: (FollowButtonState) -> Void
    private let onFollowingClick: () -> Void
    private let onFansClick: () -> Void

    public init(
        user: UiProfile,
        relation: UiState<UiRelation>,
        followButtonState: UiState<FollowButtonState>,
        isMe: UiState<KotlinBoolean>,
        onFollowClick: @escaping (FollowButtonState) -> Void,
        onFollowingClick: @escaping () -> Void,
        onFansClick: @escaping () -> Void
    ) {
        self.user = user
        self.relation = relation
        self.followButtonState = followButtonState
        self.isMe = isMe
        self.onFollowClick = onFollowClick
        self.onFollowingClick = onFollowingClick
        self.onFansClick = onFansClick
    }

    public var body: some View {
        ZStack(alignment: .top) {
            if let banner = user.banner {
                Color.clear.overlay {
                    NetworkImage(data: banner.url, customHeader: banner.customHeaders)
                        .frame(height: CommonProfileHeaderConstants.headerHeight)
                        .onTapGesture {
                            if let url = URL(
                                string: DeeplinkRoute.Media.MediaImage(
                                    uri: banner.url,
                                    previewUrl: nil,
                                    customHeaders: banner.customHeaders
                                ).toUri()
                            ) {
                                openURL.callAsFunction(url)
                            }
                        }
                }
                .frame(height: CommonProfileHeaderConstants.headerHeight)
                .clipped()
            } else {
                Rectangle()
                    .foregroundColor(.gray)
                    .frame(height: CommonProfileHeaderConstants.headerHeight)
                    .clipped()
            }
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    VStack {
                        Spacer()
                            .frame(
                                height: CommonProfileHeaderConstants.headerHeight -
                                    CommonProfileHeaderConstants.avatarSize / 2
                            )
                        AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
                            .frame(width: CommonProfileHeaderConstants.avatarSize, height: CommonProfileHeaderConstants.avatarSize)
                            .onTapGesture {
                                if let avatar = user.avatar,
                                   let url = URL(
                                       string: DeeplinkRoute.Media.MediaImage(
                                           uri: avatar.url,
                                           previewUrl: nil,
                                           customHeaders: avatar.customHeaders
                                       ).toUri()
                                   ) {
                                    openURL.callAsFunction(url)
                                }
                            }
                    }
                    Spacer()
                    VStack {
                        Spacer()
                            .frame(height: CommonProfileHeaderConstants.headerHeight)
                        if case .success(let data) = onEnum(of: isMe), !data.data.boolValue {
                            switch onEnum(of: followButtonState) {
                            case .success(let buttonState):
                                VStack(spacing: 4) {
                                    followButton(state: buttonState.data) {
                                        onFollowClick(buttonState.data)
                                    }
                                    .id(buttonState.data.id)
                                    .transition(.opacity.combined(with: .scale(scale: 0.92)))

                                    if case .success(let relationState) = onEnum(of: relation), relationState.data.isFans {
                                        Text("relation_is_fans")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                            .multilineTextAlignment(.center)
                                    }
                                }
                                .animation(.spring(response: 0.25, dampingFraction: 0.86), value: buttonState.data.id)
                            case .loading:
                                Button(action: {}, label: {
                                    Text("#loading")
                                })
                                .backport
                                .glassProminentButtonStyle()
                                .redacted(reason: .placeholder)
                            case .error:
                                EmptyView()
                            }
                        }
                    }
                }

                if shouldWrapContentInCard {
                    ListCardView {
                        content
                            .padding()
                    }
                } else {
                    content
                }
            }
            .padding(.horizontal)
        }
    }

    private var shouldWrapContentInCard: Bool {
        #if os(iOS)
        horizontalSizeClass == .compact && timelineDisplayMode != .plain
        #else
        timelineDisplayMode != .plain
        #endif
    }

    @ViewBuilder
    private func followButton(state: FollowButtonState, action: @escaping () -> Void) -> some View {
        switch onEnum(of: state) {
        case .blocked:
            Button(action: action) {
                Text(state.titleKey)
            }
            .tint(.red)
            .buttonStyle(.borderedProminent)
        case .following, .requested:
            Button(action: action) {
                Text(state.titleKey)
            }
            .backport
            .glassButtonStyle(fallbackStyle: .bordered)
        case .follow, .requestFollow:
            Button(action: action) {
                Text(state.titleKey)
            }
            .backport
            .glassProminentButtonStyle()
        }
    }

    private var content: some View {
        VStack(
            alignment: .leading,
            spacing: 8
        ) {
            RichText(text: user.name)
                .font(.headline)
                .frame(maxWidth: .infinity, alignment: .leading)
                .textSelection(.enabled)
            HStack {
                Text(user.handle.canonical)
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .textSelection(.enabled)
                ForEach(0..<user.mark.count, id: \.self) { index in
                    let mark = user.mark[index]
                    switch mark {
                    case .cat: Image(fontAwesome: .cat)
                    case .verified: Image(fontAwesome: .circleCheck)
                    case .locked: Image(fontAwesome: .lock)
                    case .bot: Image(fontAwesome: .robot)
                    }
                }
                if user.translationDisplayState != .hidden {
                    TranslateStatusComponent(data: user.translationDisplayState)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            if let desc = user.description_ {
                RichText(text: desc)
                    .textSelection(.enabled)
            }

            if let bottomContent = user.bottomContent {
                switch onEnum(of: bottomContent) {
                case .fields(let data):
                    FieldsView(fields: data.fields)
                case .iconify(let data):
                    IconFieldView(data: data)
                }
            }

            MatrixView(
                followCount: user.matrices.followsCountHumanized,
                fansCount: user.matrices.fansCountHumanized,
                onFollowingClick: onFollowingClick,
                onFansClick: onFansClick
            )
        }
    }
}

public struct ProfileHeader: View {
    private let user: UiState<UiProfile>
    private let relation: UiState<UiRelation>
    private let followButtonState: UiState<FollowButtonState>
    private let isMe: UiState<KotlinBoolean>
    private let onFollowClick: (UiProfile, FollowButtonState) -> Void
    private let onFollowingClick: (MicroBlogKey) -> Void
    private let onFansClick: (MicroBlogKey) -> Void

    public init(
        user: UiState<UiProfile>,
        relation: UiState<UiRelation>,
        followButtonState: UiState<FollowButtonState>,
        isMe: UiState<KotlinBoolean>,
        onFollowClick: @escaping (UiProfile, FollowButtonState) -> Void,
        onFollowingClick: @escaping (MicroBlogKey) -> Void,
        onFansClick: @escaping (MicroBlogKey) -> Void
    ) {
        self.user = user
        self.relation = relation
        self.followButtonState = followButtonState
        self.isMe = isMe
        self.onFollowClick = onFollowClick
        self.onFollowingClick = onFollowingClick
        self.onFansClick = onFansClick
    }

    public var body: some View {
        switch onEnum(of: user) {
        case .error:
            Text("error")
        case .loading:
            CommonProfileHeader(
                user: createSampleUser(),
                relation: relation,
                followButtonState: followButtonState,
                isMe: isMe,
                onFollowClick: { _ in },
                onFollowingClick: {},
                onFansClick: {}
            )
            .redacted(reason: .placeholder)
        case .success(let data):
            ProfileHeaderSuccess(
                user: data.data,
                relation: relation,
                followButtonState: followButtonState,
                isMe: isMe,
                onFollowClick: { followButtonState in onFollowClick(data.data, followButtonState) },
                onFollowingClick: onFollowingClick,
                onFansClick: onFansClick
            )
        }
    }
}

public struct ProfileHeaderSuccess: View {
    private let user: UiProfile
    private let relation: UiState<UiRelation>
    private let followButtonState: UiState<FollowButtonState>
    private let isMe: UiState<KotlinBoolean>
    private let onFollowClick: (FollowButtonState) -> Void
    private let onFollowingClick: (MicroBlogKey) -> Void
    private let onFansClick: (MicroBlogKey) -> Void

    public init(
        user: UiProfile,
        relation: UiState<UiRelation>,
        followButtonState: UiState<FollowButtonState>,
        isMe: UiState<KotlinBoolean>,
        onFollowClick: @escaping (FollowButtonState) -> Void,
        onFollowingClick: @escaping (MicroBlogKey) -> Void,
        onFansClick: @escaping (MicroBlogKey) -> Void
    ) {
        self.user = user
        self.relation = relation
        self.followButtonState = followButtonState
        self.isMe = isMe
        self.onFollowClick = onFollowClick
        self.onFollowingClick = onFollowingClick
        self.onFansClick = onFansClick
    }

    public var body: some View {
        CommonProfileHeader(
            user: user,
            relation: relation,
            followButtonState: followButtonState,
            isMe: isMe,
            onFollowClick: onFollowClick,
            onFollowingClick: {
                onFollowingClick(user.key)
            },
            onFansClick: {
                onFansClick(user.key)
            }
        )
    }
}

public struct MatrixView: View {
    private let followCount: String
    private let fansCount: String
    private let onFollowingClick: () -> Void
    private let onFansClick: () -> Void

    public init(
        followCount: String,
        fansCount: String,
        onFollowingClick: @escaping () -> Void,
        onFansClick: @escaping () -> Void
    ) {
        self.followCount = followCount
        self.fansCount = fansCount
        self.onFollowingClick = onFollowingClick
        self.onFansClick = onFansClick
    }

    public var body: some View {
        HStack {
            HStack {
                Text(followCount)
                Text("matrix_following")
            }
            .onTapGesture {
                onFollowingClick()
            }
            HStack {
                Text(fansCount)
                Text("matrix_followers")
            }
            .onTapGesture {
                onFansClick()
            }
        }
        .font(.caption)
        .foregroundStyle(.secondary)
    }
}

public struct IconFieldView: View {
    private let data: UiProfileBottomContentIconify

    public init(data: UiProfileBottomContentIconify) {
        self.data = data
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(data.items.keys), id: \.name) { key in
                let value = data.items[key]
                Label(
                    title: {
                        if let text = value {
                            RichText(text: text)
                                .font(.body)
                        }
                    },
                    icon: {
                        switch key {
                        case .location: Image(fontAwesome: .locationDot)
                        case .url: Image(fontAwesome: .globe)
                        case .verify: Image(fontAwesome: .circleCheck)
                        }
                    }
                )
            }
        }
    }
}

public struct FieldsView: View {
    private let fields: [String: UiRichText]

    public init(fields: [String: UiRichText]) {
        self.fields = fields
    }

    public var body: some View {
        if fields.count > 0 {
            VStack(alignment: .leading, spacing: 8) {
                let keys = fields.map {
                    $0.key
                }
                ForEach(0..<keys.count, id: \.self) { index in
                    let key = keys[index]
                    Text(key)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if let richText = fields[key] {
                        RichText(text: richText)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .font(.body)
                    }
                    if index != keys.count - 1 {
                        Divider()
                    }
                }
                .padding(.horizontal)
            }
            .padding(.vertical)
            .background(Color.flareSecondarySystemBackground)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        } else {
            EmptyView()
        }
    }
}

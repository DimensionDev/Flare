import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

enum CommonProfileHeaderConstants {
    static let headerHeight: CGFloat = 200
    static let avatarSize: CGFloat = 96
}

private enum FollowButtonState: Equatable {
    case blocked
    case following
    case requested
    case follow

    init(_ relation: UiRelation) {
        if relation.blocking {
            self = .blocked
        } else if relation.following {
            self = .following
        } else if relation.hasPendingFollowRequestFromYou {
            self = .requested
        } else {
            self = .follow
        }
    }

    var titleKey: LocalizedStringKey {
        switch self {
        case .blocked:
            "relation_blocked"
        case .following:
            "relation_following"
        case .requested:
            "relation_requested"
        case .follow:
            "relation_follow"
        }
    }
}

struct CommonProfileHeader: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.openURL) private var openURL
    let user: UiProfile
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    let onFollowingClick: () -> Void
    let onFansClick: () -> Void

    var body: some View {
        ZStack(alignment: .top) {
            if let banner = user.banner, !banner.isEmpty {
                Color.clear.overlay {
                    NetworkImage(data: banner)
                        .frame(height: CommonProfileHeaderConstants.headerHeight)
                        .onTapGesture {
                            openURL.callAsFunction(.init(string: DeeplinkRoute.Media.MediaImage(uri: banner, previewUrl: nil).toUri())!)
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
                        AvatarView(data: user.avatar)
                            .frame(width: CommonProfileHeaderConstants.avatarSize, height: CommonProfileHeaderConstants.avatarSize)
                            .onTapGesture {
                                openURL.callAsFunction(.init(string: DeeplinkRoute.Media.MediaImage(uri: user.avatar, previewUrl: nil).toUri())!)
                            }
                    }
                    Spacer()
                    VStack {
                        Spacer()
                            .frame(height: CommonProfileHeaderConstants.headerHeight)
                        if case .success(let data) = onEnum(of: isMe), !data.data.boolValue {
                            switch onEnum(of: relation) {
                            case .success(let relationState):
                                let buttonState = FollowButtonState(relationState.data)
                                VStack(spacing: 4) {
                                    followButton(state: buttonState) {
                                        onFollowClick(relationState.data)
                                    }
                                    .id(buttonState)
                                    .transition(.opacity.combined(with: .scale(scale: 0.92)))

                                    if relationState.data.isFans {
                                        Text("relation_is_fans")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                            .multilineTextAlignment(.center)
                                    }
                                }
                                .animation(.spring(response: 0.25, dampingFraction: 0.86), value: buttonState)
                            case .loading: Button(action: {}, label: {
                                Text("#loading")
                            })
                            .backport
                            .glassProminentButtonStyle()
                            .redacted(reason: .placeholder)
                            case .error: EmptyView()
                            }
                        }
                    }
                }

                if horizontalSizeClass == .compact {
                    ListCardView {
                        content
                            .padding()
                    }
                } else {
                    content
                }
            }
            .padding([.horizontal])
        }
    }

    @ViewBuilder
    private func followButton(state: FollowButtonState, action: @escaping () -> Void) -> some View {
        switch state {
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
        case .follow:
            Button(action: action) {
                Text(state.titleKey)
            }
            .backport
            .glassProminentButtonStyle()
        }
    }
    
    var content: some View {
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
                    case .cat:      Image("fa-cat")
                    case .verified: Image("fa-circle-check")
                    case .locked:   Image("fa-lock")
                    case .bot:      Image("fa-robot")
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

struct MatrixView: View {
    let followCount: String
    let fansCount: String
    let onFollowingClick: () -> Void
    let onFansClick: () -> Void
    var body: some View {
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

struct IconFieldView: View {
    let data: UiProfileBottomContentIconify
    var body: some View {
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
                        case .location: Image("fa-location-dot")
                        case .url:      Image("fa-globe")
                        case .verify:   Image("fa-circle-check")
                        }
                    }
                )
            }
        }
    }
}

struct FieldsView: View {
    let fields: [String: UiRichText]
    var body: some View {
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
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        } else {
            EmptyView()
        }
    }
}

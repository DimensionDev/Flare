import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

enum CommonProfileHeaderConstants {
    static let headerHeight: CGFloat = 200
    static let avatarSize: CGFloat = 96
}

struct CommonProfileHeader: View {
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
                            openURL.callAsFunction(.init(string: AppDeepLink.RawImage.shared.invoke(url: banner))!)
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
                                openURL.callAsFunction(.init(string: AppDeepLink.RawImage.shared.invoke(url: user.avatar))!)
                            }
                    }
                    Spacer()
                    VStack {
                        Spacer()
                            .frame(height: CommonProfileHeaderConstants.headerHeight)
                            if case .success(let data) = onEnum(of: isMe), !data.data.boolValue {
                                switch onEnum(of: relation) {
                                case .success(let relationState):
                                    Button(action: {
                                        onFollowClick(relationState.data)
                                    }, label: {
                                        let text = if relationState.data.blocking {
                                            String(localized: "relation_blocked")
                                        } else if relationState.data.following {
                                            String(localized: "relation_following")
                                        } else if relationState.data.hasPendingFollowRequestFromYou {
                                            String(localized: "relation_requested")
                                        } else {
                                            String(localized: "relation_follow")
                                        }
                                        Text(text)
                                    })
                                    .backport
                                    .glassProminentButtonStyle()
                                    if relationState.data.isFans {
                                        Text("relation_is_fans")
                                    }
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

                ListCardView {
                    VStack(
                        alignment: .leading,
                        spacing: 8
                    ) {
                        RichText(text: user.name)
                            .font(.headline)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        HStack {
                            Text(user.handle)
                                .font(.subheadline)
                                .foregroundColor(.gray)
                            ForEach(0..<user.mark.count, id: \.self) { index in
                                let mark = user.mark[index]
                                switch mark {
                                case .cat:      Image("fa-cat")
                                case .verified: Image("fa-circle-check")
                                case .locked:   Image("fa-lock")
                                case .bot:      Image("fa-robot")
                                }
                            }
                        }
                        if let desc = user.description_ {
                            RichText(text: desc)
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
                    .padding()
                }
            }
            .padding([.horizontal])
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
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if let richText = fields[key] {
                        RichText(text: richText)
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

import SwiftUI
import KotlinSharedUI
import Awesome

enum CommonProfileHeaderConstants {
    static let headerHeight: CGFloat = 200
    static let avatarSize: CGFloat = 96
}

struct CommonProfileHeader: View {
    let user: UiProfile
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void

    var body: some View {
        ZStack(alignment: .top) {
            if let banner = user.banner, !banner.isEmpty {
                Color.clear.overlay {
                    NetworkImage(data: banner)
                        .frame(height: CommonProfileHeaderConstants.headerHeight)
                }
                .frame(height: CommonProfileHeaderConstants.headerHeight)
            } else {
                Rectangle()
                    .foregroundColor(.gray)
                    .frame(height: CommonProfileHeaderConstants.headerHeight)
            }
            VStack(alignment: .leading) {
                HStack {
                    VStack {
                        Spacer()
                            .frame(
                                height: CommonProfileHeaderConstants.headerHeight -
                                CommonProfileHeaderConstants.avatarSize / 2
                            )
                        AvatarView(data: user.avatar)
                            .frame(width: CommonProfileHeaderConstants.avatarSize, height: CommonProfileHeaderConstants.avatarSize)
                    }
                    Spacer()
                    VStack {
                        Spacer()
                            .frame(height: CommonProfileHeaderConstants.headerHeight)

                            if case .success(let data) = onEnum(of: isMe), !data.data.boolValue {
                                switch onEnum(of: relation) {
                                case .success(let relationState): Button(action: {
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
                                .buttonStyle(.borderless)
                                case .loading: Button(action: {}, label: {
                                    Text("Button")
                                })
                                .buttonStyle(.borderless)
                                .redacted(reason: .placeholder)
                                case .error: EmptyView()
                                }
                            }
                    }
                }
                
                ListCardView {
                    VStack(alignment: .leading) {
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
                                case .cat: Awesome.Classic.Solid.cat.image.opacity(0.6)
                                case .verified: Awesome.Classic.Solid.circleCheck.image.opacity(0.6)
                                case .locked: Awesome.Classic.Solid.lock.image.opacity(0.6)
                                case .bot: Awesome.Classic.Solid.robot.image.opacity(0.6)
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
                                let list = data.items.map { $0.key }
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
                                            case .location: Awesome.Classic.Solid.locationDot.image
                                            case .url: Awesome.Classic.Solid.globe.image
                                            case .verify: Awesome.Classic.Solid.circleCheck.image
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        MatrixView(followCount: user.matrices.followsCountHumanized, fansCount: user.matrices.fansCountHumanized)
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
    var body: some View {
        HStack {
            Text(followCount)
            Text("matrix_following")
            Divider()
            Text(fansCount)
            Text("matrix_followers")
        }
        .font(.caption)
    }
}

struct FieldsView: View {
    let fields: [String: UiRichText]
    var body: some View {
        if fields.count > 0 {
            VStack(alignment: .leading) {
                let keys = fields.map {
                    $0.key
                }
                ForEach(0..<keys.count, id: \.self) { index in
                    let key = keys[index]
                    Text(key)
                        .font(.caption)
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
            #if os(iOS)
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

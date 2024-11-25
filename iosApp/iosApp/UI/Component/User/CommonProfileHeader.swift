import SwiftUI
import MarkdownUI
import NetworkImage
import shared
import Awesome
import Foundation

// Generated
@_exported import Generated

enum CommonProfileHeaderConstants {
    static let headerHeight: CGFloat = 200
    static let avatarSize: CGFloat = 96
}

struct CompactLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 4) {
            configuration.icon
            configuration.title
        }
    }
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
                    NetworkImage(url: URL(string: banner)) { image in
                        image.resizable().scaledToFill()
                            .frame(height: CommonProfileHeaderConstants.headerHeight)
                            .clipped()
                    }
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
                        UserAvatar(data: user.avatar, size: CommonProfileHeaderConstants.avatarSize)
                    }
                    VStack(alignment: .leading) {
                        Spacer()
                            .frame(height: CommonProfileHeaderConstants.headerHeight)
                        Markdown(user.name.markdown)
                            .font(.headline)
                            .markdownInlineImageProvider(.emoji)
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
                if let desc = user.description_?.markdown {
                    Markdown(desc)
                        .markdownInlineImageProvider(.emoji)
                }

                MatrixView(followCount: user.matrices.followsCountHumanized, fansCount: user.matrices.fansCountHumanized)

                
                if let bottomContent = user.bottomContent {
                    switch onEnum(of: bottomContent) {
                    case .fields(let data):
                        FieldsView(fields: data.fields)
                    case .iconify(let data):
                        HStack(spacing: 8) {
                            if let locationValue = data.items[.location] {
                                    Label(
                                    title: {
                                        Markdown(locationValue.markdown)
                                            .font(.caption2)
                                            .markdownInlineImageProvider(.emoji)
                                    },
                                        icon: {
                                            Image(uiImage: Asset.Image.Attributes.location.image
                                                .withRenderingMode(.alwaysTemplate))
                                            .imageScale(.small)
                                        }
                                    )
                                    .labelStyle(CompactLabelStyle())
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color(.systemGray6))
                                .cornerRadius(6)
                                        }
                            
                            if let urlValue = data.items[.url] {
                                    Label(
                                    title: {
                                        Markdown(urlValue.markdown)
                                            .font(.caption2)
                                            .markdownInlineImageProvider(.emoji)
                                    },
                                        icon: {
                                            Image(uiImage: Asset.Image.Attributes.globe.image
                                                .withRenderingMode(.alwaysTemplate))
                                            .imageScale(.small)
                                        }
                                    )
                                    .labelStyle(CompactLabelStyle())
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color(.systemGray6))
                                .cornerRadius(6)
                            }
                            
//                            if let verifyValue = data.items[.verify] {
//                                Label(
//                                    title: {
//                                        Markdown(verifyValue.markdown)
//                                            .font(.footnote)
//                                            .markdownInlineImageProvider(.emoji)
//                                    },
//                                    icon: {
//                                        Image("attributes/calendar").renderingMode(.template)
//                                    }
//                                )
//                                .labelStyle(CompactLabelStyle())
//                                .padding(.horizontal, 8)
//                                .padding(.vertical, 4)
//                                .background(Color(.systemGray6))
//                                .cornerRadius(6)
//                            }
                        }
                    }
                }

            }
            .padding([.horizontal])
        }
    }
}

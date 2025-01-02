import SwiftUI
import MarkdownUI
import Kingfisher
import shared
import Awesome
import Foundation

// Generated
@_exported import Generated

enum CommonProfileHeaderConstants {
    static let headerHeight: CGFloat = 200
    static let avatarSize: CGFloat = 60
}

struct CompactLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 4) {
            configuration.icon
            configuration.title
        }
    }
}

/*
 * CommonProfileHeader User Profile header(banner -- avatar -- desc -- follow count -- user location/url)
 */
struct CommonProfileHeader: View {
    let userInfo: ProfileUserInfo
    let state: ProfileState?
    let onFollowClick: (UiRelation) -> Void
    @State private var isBannerValid: Bool = true

    var body: some View {
        // banner
        ZStack(alignment: .top) {
            if let banner = userInfo.profile.banner, !banner.isEmpty && banner.range(of: "^https?://.*example\\.com.*$", options: .regularExpression) == nil && isBannerValid {
                Color.clear.overlay {
                    KFImage(URL(string: banner))
                        .onSuccess { result in
                            if result.image.size.width <= 10 || result.image.size.height <= 10 {
                                isBannerValid = false
                            }
                        }
                        .resizable()
                        .scaledToFill()
                        .frame(height: CommonProfileHeaderConstants.headerHeight)
                        .clipped()
                }.ignoresSafeArea()
                .frame(height: CommonProfileHeaderConstants.headerHeight)
            } else {
                DynamicBannerBackground(avatarUrl: userInfo.profile.avatar).ignoresSafeArea()
            }
            //user avatar
            VStack(alignment: .leading) {

         Spacer().frame(height: 16)

                HStack {
                    //avatar
                    VStack {
                        Spacer()
                            .frame(
                                height: CommonProfileHeaderConstants.headerHeight -
                                CommonProfileHeaderConstants.avatarSize / 2
                            )
                        UserAvatar(data: userInfo.profile.avatar, size: CommonProfileHeaderConstants.avatarSize)
                    }
 
                    //user name
                    VStack(alignment: .leading) {
                        Spacer()
                            .frame(height: CommonProfileHeaderConstants.headerHeight)
                        Markdown(userInfo.profile.name.markdown)
                            .font(.headline)
                            .markdownInlineImageProvider(.emoji)
                        HStack {
                            Text(userInfo.profile.handle)
                                .font(.subheadline)
                                .foregroundColor(.gray)
                            ForEach(0..<userInfo.profile.mark.count, id: \.self) { index in
                                let mark = userInfo.profile.mark[index]
                                switch mark {
                                case .cat: Awesome.Classic.Solid.cat.image.opacity(0.6)
                                case .verified: Awesome.Classic.Solid.circleCheck.image.opacity(0.6)
                                case .locked: Awesome.Classic.Solid.lock.image.opacity(0.6)
                                case .bot: Awesome.Classic.Solid.robot.image.opacity(0.6)
                                }
                            }
                        }
//                        Spacer()
//                            .frame(height: CommonProfileHeaderConstants.headerHeight)
                        HStack {
                            UserFollowsFansCount(
                                followCount: userInfo.followCount,
                                fansCount: userInfo.fansCount
                            )

                            Spacer()
                            if !userInfo.isMe {
                                if let relation = userInfo.relation {
                                    Button(action: {
                                        onFollowClick(relation)
                                    }, label: {
                                        let text = if relation.blocking {
                                            String(localized: "profile_header_button_blockedblocked")
                                        } else if relation.following {
                                            String(localized: "profile_header_button_following")
                                        } else if relation.hasPendingFollowRequestFromYou {
                                            String(localized: "profile_header_button_requested")
                                        } else {
                                            String(localized: "profile_header_button_follow")
                                        }
                                        Text(text)
                                            .font(.caption)
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 6)
                                            .background(Color.gray.opacity(0.2))
                                            .clipShape(RoundedRectangle(cornerRadius: 15))
                                    })
                                    .buttonStyle(.borderless)
                                }
                            }
                        }
                    }
                    Spacer()
                    // user relation
                    VStack {

                    }
                }
                Spacer()
                Spacer()

                //user desc
                if let desc = userInfo.profile.description_?.markdown {
                    Markdown(desc)
                        .markdownInlineImageProvider(.emoji)
                }
                Spacer()
                
                //user follows -  user fans
//                MatrixView(followCount: userInfo.profile.matrices.followsCountHumanized, fansCount: userInfo.profile.matrices.fansCountHumanized)

                Spacer()
                Spacer()

                //user Location  user url
                if let bottomContent = userInfo.profile.bottomContent {
                    switch onEnum(of: bottomContent) {
                    case .fields(let data):
                        // pawoo  的 一些个人 table Info List
                        UserInfoFieldsView(fields: data.fields)
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
       .toolbar {
        if let state = state {
            if case .success(let isMe) = onEnum(of: state.isMe), !isMe.data.boolValue {
                Menu {
                    if case .success(let user) = onEnum(of: state.userState) {
                        if case .success(let relation) = onEnum(of: state.relationState),
                           case .success(let actions) = onEnum(of: state.actions),
                           actions.data.size > 0
                        {
                            ForEach(0..<actions.data.size, id: \.self) { index in
                                let item = actions.data.get(index: index)
                                Button(action: {
                                    Task {
                                        try? await item.invoke(userKey: user.data.key, relation: relation.data)
                                    }
                                }, label: {
                                    let text = switch onEnum(of: item) {
                                    case .block(let block): if block.relationState(relation: relation.data) {
                                        String(localized: "unblock")
                                    } else {
                                        String(localized: "block")
                                    }
                                    case .mute(let mute): if mute.relationState(relation: relation.data) {
                                        String(localized: "unmute")
                                    } else {
                                        String(localized: "mute")
                                    }
                                    }
                                    let icon = switch onEnum(of: item) {
                                    case .block(let block): if block.relationState(relation: relation.data) {
                                        "xmark.circle"
                                    } else {
                                        "checkmark.circle"
                                    }
                                    case .mute(let mute): if mute.relationState(relation: relation.data) {
                                        "speaker"
                                    } else {
                                        "speaker.slash"
                                    }
                                    }
                                    Label(text, systemImage: icon)
                                })
                            }
                        }
                        Button(action: { state.report(userKey: user.data.key) }, label: {
                            Label("report", systemImage: "exclamationmark.bubble")
                        })
                    }
                } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
        }
    }
}

struct DynamicBannerBackground: View {
    let avatarUrl: String
    
    var body: some View {
        ZStack {
            // 放大的头像背景
            KFImage(URL(string: avatarUrl))
                .resizable()
                .scaledToFill()
                .frame(height: CommonProfileHeaderConstants.headerHeight)
                .blur(radius: 10)
                .overlay {
                    // 添加渐变遮罩
                    LinearGradient(
                        gradient: Gradient(colors: [
                            Color.black.opacity(0.3),
                            Color.black.opacity(0.1),
                            Color.black.opacity(0.3)
                        ]),
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                }
                .clipped()
        }
        .frame(height: CommonProfileHeaderConstants.headerHeight)
    }
}

extension UIImage {
    var averageColor: UIColor? {
        guard let inputImage = CIImage(image: self) else { return nil }
        let extentVector = CIVector(x: inputImage.extent.origin.x,
                                  y: inputImage.extent.origin.y,
                                  z: inputImage.extent.size.width,
                                  w: inputImage.extent.size.height)

        guard let filter = CIFilter(name: "CIAreaAverage",
                                  parameters: [kCIInputImageKey: inputImage,
                                             kCIInputExtentKey: extentVector]) else { return nil }
        guard let outputImage = filter.outputImage else { return nil }

        var bitmap = [UInt8](repeating: 0, count: 4)
        let context = CIContext(options: [.workingColorSpace: kCFNull as Any])
        context.render(outputImage,
                      toBitmap: &bitmap,
                      rowBytes: 4,
                      bounds: CGRect(x: 0, y: 0, width: 1, height: 1),
                      format: .RGBA8,
                      colorSpace: nil)

        return UIColor(red: CGFloat(bitmap[0]) / 255,
                      green: CGFloat(bitmap[1]) / 255,
                      blue: CGFloat(bitmap[2]) / 255,
                      alpha: CGFloat(bitmap[3]) / 255)
    }
}

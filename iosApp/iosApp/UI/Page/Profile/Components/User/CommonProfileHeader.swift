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
    let user: UiProfile
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    @State private var isBannerValid: Bool = true

    var body: some View {
        // banner
        ZStack(alignment: .top) {
            if let banner = user.banner, !banner.isEmpty && banner.range(of: "^https?://.*example\\.com.*$", options: .regularExpression) == nil && isBannerValid {
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
                }
                .frame(height: CommonProfileHeaderConstants.headerHeight)
            } else {
                DynamicBannerBackground(avatarUrl: user.avatar)
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
                        UserAvatar(data: user.avatar, size: CommonProfileHeaderConstants.avatarSize)
                    }
 
                    //user name
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
//                        Spacer()
//                            .frame(height: CommonProfileHeaderConstants.headerHeight)
                        HStack {
                            MatrixView(followCount: user.matrices.followsCountHumanized, fansCount: user.matrices.fansCountHumanized)

                            Spacer()
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
                                        .font(.caption)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(Color.gray.opacity(0.2))
                                        .clipShape(RoundedRectangle(cornerRadius: 15))
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
                    Spacer()
                    // user relation
                    VStack {
                     
                    }
                }
                Spacer()
                Spacer()

                //user desc
                if let desc = user.description_?.markdown {
                    Markdown(desc)
                        .markdownInlineImageProvider(.emoji)
                }
                Spacer()
                
                //user follows -  user fans
//                MatrixView(followCount: user.matrices.followsCountHumanized, fansCount: user.matrices.fansCountHumanized)

                Spacer()
                Spacer()

                //user Location  user url
                if let bottomContent = user.bottomContent {
                    switch onEnum(of: bottomContent) {
                    case .fields(let data):
                        // pawoo  的 一些个人 table Info List
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

import SwiftUI
import MarkdownUI
import NetworkImage

enum CommonProfileHeaderConstants {
    static let headerHeight: CGFloat = 200
    static let avatarSize: CGFloat = 96
}


struct CommonProfileHeader<HeaderTrailing, HandleTrailing, Content>: View where HeaderTrailing: View, HandleTrailing: View, Content: View {
    let bannerUrl: String?
    let avatarUrl: String?
    let displayName: String
    let handle: String
    let description: String?
    @ViewBuilder let headerTrailing: () -> HeaderTrailing
    @ViewBuilder let handleTrailing: () -> HandleTrailing
    @ViewBuilder let content: () -> Content
    
    init(bannerUrl: String?, avatarUrl: String?, displayName: String, handle: String, description: String?, headerTrailing: @escaping () -> HeaderTrailing = { EmptyView() }, handleTrailing: @escaping () -> HandleTrailing = { EmptyView() }, content: @escaping () -> Content = { EmptyView() }) {
        self.bannerUrl = bannerUrl
        self.avatarUrl = avatarUrl
        self.displayName = displayName
        self.handle = handle
        self.description = description
        self.headerTrailing = headerTrailing
        self.handleTrailing = handleTrailing
        self.content = content
    }
    
    var body: some View {
        ZStack(alignment: .top) {
            if let banner = bannerUrl, !banner.isEmpty {
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
                            .frame(height: CommonProfileHeaderConstants.headerHeight - CommonProfileHeaderConstants.avatarSize / 2)
                        if let avatar = avatarUrl {
                            UserAvatar(data: avatar, size: CommonProfileHeaderConstants.avatarSize)
                        } else {
                            Rectangle()
                                .foregroundColor(.accentColor)
                                .frame(width: CommonProfileHeaderConstants.avatarSize, height: CommonProfileHeaderConstants.avatarSize)
                                .clipShape(.circle)
                        }
                    }
                    VStack(alignment: .leading) {
                        Spacer()
                            .frame(height: CommonProfileHeaderConstants.headerHeight)
                        Markdown(displayName)
                            .font(.headline)
                            .markdownInlineImageProvider(.emoji)
                        HStack {
                            Text(handle)
                                .font(.subheadline)
                                .foregroundColor(.gray)
                            handleTrailing()
                        }
                    }
                    Spacer()
                    VStack {
                        Spacer()
                            .frame(height: CommonProfileHeaderConstants.headerHeight)
                        headerTrailing()
                    }
                }
                if let desc = description {
                    Markdown(desc)
                        .markdownInlineImageProvider(.emoji)
                }
                content()
            }
            .padding([.horizontal])
        }
    }
    
    
}

#Preview {
    List {
        CommonProfileHeader(bannerUrl: "https://media.mstdn.jp/accounts/headers/000/927/711/original/5d3f32c897d47597.jpeg", avatarUrl: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg", displayName: "test", handle: "test@test.test", description: "tefewfewfewfewfewst", headerTrailing: {
            Text("header")
        }, handleTrailing: {
            Text("handle")
        }, content: {
            Text("content")
        })
            .listRowInsets(EdgeInsets())
    }.listStyle(.plain)
        .edgesIgnoringSafeArea(.top)
}


#Preview {
    List {
        CommonProfileHeader(bannerUrl: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", avatarUrl: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg", displayName: "test", handle: "test@test.test", description: "tefewfewfewfewfewst", headerTrailing: {
            Button(action: /*@START_MENU_TOKEN@*/{}/*@END_MENU_TOKEN@*/, label: {
                Text("header")
            })
            .buttonStyle(.borderless)
        }, handleTrailing: {
            Text("handle")
        }, content: {
            Text("content")
        })
            .listRowInsets(EdgeInsets())
    }.listStyle(.plain)
        .edgesIgnoringSafeArea(.top)
}
#Preview {
    CommonProfileHeader(bannerUrl: nil, avatarUrl: nil, displayName: "test", handle: "test@test.test", description: "test")
}

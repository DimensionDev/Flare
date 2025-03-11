import shared
import SwiftUI

struct StatusTimelineComponent: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let data: PagingState<UiTimeline>
    let detailKey: MicroBlogKey?
    var body: some View {
        switch onEnum(of: data) {
        case .empty: Text("timeline_load_empty", comment: "Timeline is empty")
        case let .error(error):
            Text("timeline_load_error", comment: "Timeline loading error")
            Text(error.error.message ?? "")
        case .loading:
            ForEach((-10) ... -1, id: \.self) { _ in
                StatusPlaceHolder()
                    .if(horizontalSizeClass != .compact) { view in
                        view.padding([.horizontal])
                    }
            }
        case let .success(success):
            ForEach(0 ..< success.itemCount, id: \.self) { index in
                let data = success.peek(index: index)
                VStack(spacing: 0) {
                    if let status = data {
                        StatusItemView(
                            data: status,
                            detailKey: detailKey
                        )
                        .padding(.vertical, 8)
                    } else {
                        StatusPlaceHolder()
                    }
                    
                    if index < success.itemCount - 1 {
                        Divider()
                            .background(Color(UIColor.separator))
                            .padding(.horizontal, 0)
                    }
                }
                .onAppear {
                    success.get(index: index)
                }
                .if(horizontalSizeClass != .compact) { view in
                    view.padding([.horizontal])
                }
            }
        }
    }
}

struct StatusItemView: View {
    @Environment(\.openURL) private var openURL
    let data: UiTimeline
    let detailKey: MicroBlogKey?
    let enableTranslation: Bool

    init(data: UiTimeline, detailKey: MicroBlogKey?, enableTranslation: Bool = true) {
        self.data = data
        self.detailKey = detailKey
        self.enableTranslation = enableTranslation
    }

    var body: some View {
        if let topMessage = data.topMessage {
            Button(action: {
                topMessage.user?.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
            }, label: {
                StatusRetweetHeaderComponent(topMessage: topMessage)
            })
            .buttonStyle(.plain)
        }
        if let content = data.content {
            switch onEnum(of: content) {
            case let .status(data): Button(action: {
                    if detailKey != data.statusKey {
                        data.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                    }
                }, label: {
                    CommonTimelineStatusComponent(
                        data: data,
                        onMediaClick: { index, media in
                            data.onMediaClicked(.init(launcher: AppleUriLauncher(openURL: openURL)), media, KotlinInt(integerLiteral: index))
                        },
                        isDetail: detailKey == data.statusKey,
                        enableTranslation: enableTranslation
                    )
                })
                .buttonStyle(.plain)
            case let .user(data):
                HStack {
                    UserComponent(
                        user: data.value,
                        topEndContent: nil,
                        onUserClicked: {
                            data.value.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                        }
                    )
                    Spacer()
                }
            case let .userList(data):
                HStack {
                    ForEach(data.users, id: \.key) { user in
                        UserAvatar(data: user.avatar, size: 48)
                    }
                }
            case .feed: EmptyView()
            }
        }
    }
}

struct StatusPlaceHolder: View {
    var body: some View {
        StatusItemView(
            data: createSampleStatus(
                user: createSampleUser()
            ),
            detailKey: nil
        )
        .redacted(reason: .placeholder)
    }
}

import SwiftUI
import shared

struct StatusTimelineComponent: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let data: PagingState<UiTimeline>
    let detailKey: MicroBlogKey?
    var body: some View {
        switch onEnum(of: data) {
        case .empty: Text("timeline_load_empty", comment: "Timeline is empty")
        case .error(let error):
            Text("timeline_load_error", comment: "Timeline loading error")
            Text(error.error.message ?? "")
        case .loading:
            ForEach((-10)...(-1), id: \.self) { _ in
                StatusPlaceHolder()
                    .if(horizontalSizeClass != .compact) { view in
                        view.padding([.horizontal])
                    }
            }
        case .success(let success):
            ForEach(0..<success.itemCount, id: \.self) { index in
                let data = success.peek(index: index)
                VStack {
                    if let status = data {
                        StatusItemView(
                            data: status,
                            detailKey: detailKey
                        )
                    } else {
                        StatusPlaceHolder()
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
            case .status(let data): Button(action: {
                if detailKey != data.statusKey {
                    data.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                }
            }, label: {
                CommonStatusComponent(
                    data: data,
                    onMediaClick: { index, media in
                        data.onMediaClicked(.init(launcher: AppleUriLauncher(openURL: openURL)), media, KotlinInt(integerLiteral: index))
                    },
                    isDetail: detailKey == data.statusKey
                )
            })
            .buttonStyle(.plain)
            case .user(let data):
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
            case .userList(let data):
                HStack {
                    ForEach(data.users, id: \.key) { user in
                        UserAvatar(data: user.avatar, size: 48)
                    }
                }
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

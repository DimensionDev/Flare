import SwiftUI
import KotlinSharedUI
import FlareAppleCore

/// The native list owns paging, layout and scrolling; screens supply their content.
public struct TimelineListRequest {
    public enum Content {
        case posts(PagingState<UiTimelineV2>)
        case users(PagingState<UiProfile>)
        case none
    }

    public let key: String
    public let content: Content
    public let headers: [TimelineListHeader]

    public init(key: String, content: Content, headers: [TimelineListHeader] = []) {
        self.key = key
        self.content = content
        self.headers = headers
    }
}

public struct TimelineListHeader {
    public let id: String
    public let view: AnyView
    public let isPinned: Bool

    public init<Content: View>(id: String, isPinned: Bool = false, @ViewBuilder content: () -> Content) {
        self.id = id
        self.view = AnyView(content())
        self.isPinned = isPinned
    }

    public static func title(_ key: String) -> Self {
        Self(id: key, isPinned: true) {
            Text(FlareAppleUILocalization.string(key))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Color.flareSystemGroupedBackground)
        }
    }
}

public struct TimelineListRenderer {
    private let render: (TimelineListRequest) -> AnyView

    public init<Content: View>(@ViewBuilder render: @escaping (TimelineListRequest) -> Content) {
        self.render = { AnyView(render($0)) }
    }

    public func callAsFunction(_ request: TimelineListRequest) -> AnyView { render(request) }
}

public extension EnvironmentValues {
    @Entry var timelineListRenderer: TimelineListRenderer? = nil
}

struct TimelineListUserStrip: View {
    let users: PagingStateSuccess<UiProfile>
    @Environment(\.openURL) private var openURL
    @ScaledMetric(relativeTo: .body) private var rowHeight: CGFloat = 76

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 8) {
                ForEach(0..<Int(users.itemCount), id: \.self) { index in
                    ListCardView {
                        Group {
                            if let user = users.peek(index: Int32(index)) {
                                UserCompatView(data: user)
                                    .onTapGesture {
                                        user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                                    }
                            } else {
                                UserLoadingView()
                            }
                        }
                        .padding(16)
                    }
                    .frame(width: 280)
                    .onAppear { _ = users.get(index: Int32(index)) }
                }
            }
            .padding(.horizontal, 16)
        }
        .frame(height: max(rowHeight, 76))
        .scrollIndicators(.hidden)
    }
}

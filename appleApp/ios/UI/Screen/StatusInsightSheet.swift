import SwiftUI
import FlareAppleUI
import KotlinSharedUI
import FlareAppleCore

struct StatusInsightSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.timelineAppearance) private var timelineAppearance
    @StateObject private var presenter: KotlinPresenter<StatusInsightPresenterState>
    let onNavigate: (Route) -> Void

    var body: some View {
        AgentChatView(
            messages: Array(presenter.state.messages),
            isRunning: presenter.state.room.isRunning,
            canSend: presenter.state.canSend,
            errorMessage: presenter.state.room.errorMessage,
            runningTrace: presenter.state.room.currentTrace?.localizedLabel ?? String(localized: "status_insight_analyzing"),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder"),
            onInputChange: presenter.state.setInput,
            onSend: presenter.state.sendMessage,
            onInputRequestOptionSelected: presenter.state.selectInputRequestOption,
            onPostClick: { post in
                onNavigate(.statusDetail(post.accountType, post.statusKey))
            },
            onUserClick: { user in
                if let route = agentRoute(for: user) {
                    onNavigate(route)
                }
            }
        )
        .navigationTitle(String(localized: "status_insight_title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(
                    role: .cancel
                ) {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel")
                    } icon: {
                        Image(fontAwesome: .xmark)
                    }
                }
            }
        }
    }
}

extension StatusInsightSheet {
    init(
        accountType: AccountType,
        statusKey: MicroBlogKey,
        onNavigate: @escaping (Route) -> Void = { _ in }
    ) {
        self.onNavigate = onNavigate
        self._presenter = .init(
            wrappedValue: .init(
                presenter: StatusInsightPresenter(
                    accountType: accountType,
                    statusKey: statusKey
                )
            )
        )
    }
}

struct StatusInsightPostPreview: View {
    @Environment(\.timelineAppearance) private var timelineAppearance
    let post: UiTimelineV2.Post
    let onClick: (() -> Void)?

    init(post: UiTimelineV2.Post, onClick: (() -> Void)? = nil) {
        self.post = post
        self.onClick = onClick
    }

    var body: some View {
        StatusView(
            data: post,
            isQuote: true,
            showMedia: false,
            maxLine: 3,
            showExpandTextButton: false,
            forceHideActions: true,
            showTranslate: false,
            showParents: false
        )
        .padding(12)
        .environment(\.timelineAppearance, timelineAppearance.withStatusInsightPreviewDefaults())
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color(.separator), lineWidth: 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: 12))
        .if(onClick != nil) { view in
            view.onTapGesture {
                onClick?()
            }
        }
    }
}

private func agentRoute(for user: UiProfile) -> Route? {
    guard let event = user.clickEvent as? ClickEventDeeplink else {
        return nil
    }
    return Route.fromDeepLink(url: event.url)
}

struct StatusInsightCurrentTrace: View {
    let trace: String

    var body: some View {
        HStack(spacing: 8) {
            Image(fontAwesome: .robot)
            Text(verbatim: trace)
                .font(.body)
                .shimmeringText()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

private struct ShimmeringTextModifier: ViewModifier {
    @State private var phase: CGFloat = -1

    func body(content: Content) -> some View {
        content
            .foregroundStyle(.secondary)
            .overlay {
                GeometryReader { proxy in
                    LinearGradient(
                        colors: [
                            .secondary.opacity(0.35),
                            .primary,
                            .secondary.opacity(0.35),
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: max(proxy.size.width * 0.65, 120))
                    .offset(x: phase * proxy.size.width)
                }
                .mask(content)
            }
            .onAppear {
                phase = -1
                withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) {
                    phase = 1.4
                }
            }
    }
}

private extension View {
    func shimmeringText() -> some View {
        modifier(ShimmeringTextModifier())
    }
}

private extension TimelineAppearance {
    func withStatusInsightPreviewDefaults() -> TimelineAppearance {
        doCopy(
            avatarShape: avatarShape,
            showMedia: false,
            showSensitiveContent: showSensitiveContent,
            expandContentWarning: true,
            expandMediaSize: false,
            videoAutoplay: .never,
            showLinkPreview: false,
            compatLinkPreview: compatLinkPreview,
            showNumbers: showNumbers,
            postActionStyle: .hidden,
            postActionLayout: postActionLayout,
            fullWidthPost: fullWidthPost,
            absoluteTimestamp: absoluteTimestamp,
            showPlatformLogo: showPlatformLogo,
            timelineDisplayMode: timelineDisplayMode,
            aiConfig: aiConfig,
            lineLimit: lineLimit,
            showTranslateButton: showTranslateButton
        )
    }
}

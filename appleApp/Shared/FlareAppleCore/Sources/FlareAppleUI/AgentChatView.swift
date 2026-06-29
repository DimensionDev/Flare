import FlareAppleCore
import KotlinSharedUI
import SwiftUI
import SwiftUIBackports
import Textual

public struct AgentChatView: View {
    let messages: PagingState<AgentChatHistoryMessage>
    let isRunning: Bool
    let canSend: Bool
    let errorMessage: String?
    let runningTrace: String
    let inputPlaceholder: String
    let onInputChange: (String) -> Void
    let onSend: () -> Void
    let onInputRequestOptionSelected: (AgentInputRequest.Option) -> Void
    let onPostClick: (UiTimelineV2.Post) -> Void
    let onUserClick: (UiProfile) -> Void
    let showsEmptyPlaceholder: Bool

    @State private var draft = ""

    public init(
        messages: PagingState<AgentChatHistoryMessage>,
        isRunning: Bool,
        canSend: Bool,
        errorMessage: String?,
        runningTrace: String,
        inputPlaceholder: String,
        onInputChange: @escaping (String) -> Void,
        onSend: @escaping () -> Void,
        onInputRequestOptionSelected: @escaping (AgentInputRequest.Option) -> Void,
        onPostClick: @escaping (UiTimelineV2.Post) -> Void,
        onUserClick: @escaping (UiProfile) -> Void,
        showsEmptyPlaceholder: Bool = true
    ) {
        self.messages = messages
        self.isRunning = isRunning
        self.canSend = canSend
        self.errorMessage = errorMessage
        self.runningTrace = runningTrace
        self.inputPlaceholder = inputPlaceholder
        self.onInputChange = onInputChange
        self.onSend = onSend
        self.onInputRequestOptionSelected = onInputRequestOptionSelected
        self.onPostClick = onPostClick
        self.onUserClick = onUserClick
        self.showsEmptyPlaceholder = showsEmptyPlaceholder
    }

    public var body: some View {
        ZStack {
            if isEmptyPlaceholderVisible {
                AgentChatEmptyPlaceholder()
            } else {
                switch onEnum(of: messages) {
                case .loading:
                    AgentChatMessagesSkeleton()
                default:
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 10) {
                            PagingView(
                                data: messages,
                                reversed: true,
                                successContent: { message in
                                    AgentChatMessageBubble(
                                        parts: Array(message.parts),
                                        isUser: message.isUser,
                                        onInputRequestOptionSelected: onInputRequestOptionSelected,
                                        onPostClick: onPostClick,
                                        onUserClick: onUserClick
                                    )
                                },
                                loadingContent: {
                                    ProgressView()
                                },
                                errorContent: { error, retry in
                                    ListErrorView(error: error, onRetry: retry)
                                        .padding()
                                        .frame(maxWidth: .infinity, alignment: .center)
                                },
                                emptyContent: {
                                    EmptyView()
                                }
                            )
                        }
                        .padding()
                    }
                    .defaultScrollAnchor(.bottom)
                }
            }
        }
        .background(Color.flareSystemGroupedBackground)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 0) {
                if isRunning {
                    StatusInsightCurrentTrace(trace: runningTrace)
                        .padding(.horizontal)
                }
                if let errorMessage {
                    Text(verbatim: errorMessage)
                        .foregroundStyle(.red)
                        .padding(.horizontal)
                }

                AgentChatInputBar(
                    draft: $draft,
                    inputPlaceholder: inputPlaceholder,
                    canSend: canSend,
                    onInputChange: onInputChange,
                    onSend: submit
                )
            }
        }
    }

    private var isEmptyPlaceholderVisible: Bool {
        guard showsEmptyPlaceholder, errorMessage == nil else {
            return false
        }
        if case .empty = onEnum(of: messages) {
            return true
        }
        return false
    }

    private func submit() {
        guard canSend else { return }
        onSend()
        draft = ""
    }
}

private struct AgentChatEmptyPlaceholder: View {
    var body: some View {
        ContentUnavailableView {
            Label {
                Text("agent_chat_empty_title", bundle: .main)
            } icon: {
                Image(fontAwesome: .robot)
            }
        } description: {
            Text("agent_chat_empty_description", bundle: .main)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 40)
    }
}

private struct AgentChatMessagesSkeleton: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(0..<3, id: \.self) { index in
                    AgentChatMessageSkeletonBubble(isUser: index == 0)
                }
            }
            .padding()
        }
        .defaultScrollAnchor(.bottom)
    }
}

private struct AgentChatMessageSkeletonBubble: View {
    let isUser: Bool

    var body: some View {
        ZStack(alignment: isUser ? .trailing : .leading) {
            VStack(alignment: .leading, spacing: 8) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(.quaternary)
                    .frame(width: 280, height: 14)
                RoundedRectangle(cornerRadius: 4)
                    .fill(.quaternary)
                    .frame(width: 180, height: 14)
            }
            .padding(12)
            .background(Color.flareSecondarySystemGroupedBackground, in: RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color.flareSeparator.opacity(0.35), lineWidth: 1)
            )
        }
        .frame(maxWidth: .infinity, alignment: isUser ? .trailing : .leading)
    }
}

private struct AgentChatInputBar: View {
    @Binding var draft: String
    let inputPlaceholder: String
    let canSend: Bool
    let onInputChange: (String) -> Void
    let onSend: () -> Void

    var body: some View {
        TextField(inputPlaceholder, text: $draft, axis: .vertical)
            .lineLimit(1...5)
            .textFieldStyle(.plain)
            .padding()
            .onSubmit {
                onSend()
            }
            .safeAreaInset(edge: .trailing, spacing: 8) {
                Button(action: onSend) {
                    Image(systemName: "paperplane.fill")
                        .frame(width: 24, height: 24)
                }
                .buttonBorderShape(.circle)
                .backport
                .glassButtonStyle()
                .disabled(!canSend)
                .help(String(localized: "agent_chat_send", bundle: .main))
                .accessibilityLabel(Text("agent_chat_send", bundle: .main))
                .padding(.trailing, 8)
            }
            .backport
            .glassEffect()
            .padding(.horizontal)
            .padding(.bottom)
            .onChange(of: draft) { _, value in
                onInputChange(value)
            }
    }
}

private struct AgentChatMessageBubble: View {
    let parts: [AgentMessagePart]
    let isUser: Bool
    let onInputRequestOptionSelected: (AgentInputRequest.Option) -> Void
    let onPostClick: (UiTimelineV2.Post) -> Void
    let onUserClick: (UiProfile) -> Void

    var body: some View {
        HStack {
            if isUser {
                Spacer(minLength: 80)
            }

            if isPreviewOnlyUserMessage {
                messageContent
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                messageContent
                    .textSelection(.enabled)
                    .padding(12)
                    .foregroundStyle(isUser ? .white : .primary)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(isUser ? Color.accentColor : Color.flareSecondarySystemGroupedBackground)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(isUser ? Color.clear : Color.flareSeparator.opacity(0.45), lineWidth: 1)
                    )
            }

            if !isUser {
                Spacer(minLength: 80)
            }
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var messageContent: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(parts.enumerated()), id: \.offset) { _, part in
                switch part {
                case let textPart as AgentMessagePartText:
                    markdownText(textPart.markdown)
                        .fixedSize(horizontal: false, vertical: true)
                case let postPart as AgentMessagePartPostCard:
                    StatusInsightPostPreview(
                        post: postPart.post,
                        onClick: {
                            onPostClick(postPart.post)
                        }
                    )
                case let userPart as AgentMessagePartUserCard:
                    AgentUserPreview(
                        user: userPart.user,
                        onClick: {
                            onUserClick(userPart.user)
                        }
                    )
                case let actionsPart as AgentMessagePartActions:
                    if !isUser {
                        AgentInputRequestOptionsView(
                            request: actionsPart.request,
                            enabled: !actionsPart.selected,
                            selectedOptionId: actionsPart.selectedOptionId,
                            onOptionSelected: onInputRequestOptionSelected
                        )
                    }
                default:
                    EmptyView()
                }
            }
        }
    }

    @ViewBuilder
    private func markdownText(_ value: String) -> some View {
        if isUser {
            Text(verbatim: value)
        } else {
            StructuredText(markdown: value)
                .textual.textSelection(.enabled)
        }
    }

    private var isPreviewOnlyUserMessage: Bool {
        isUser && !parts.isEmpty && parts.allSatisfy { part in
            part is AgentMessagePartPostCard || part is AgentMessagePartUserCard
        }
    }
}

private struct AgentInputRequestOptionsView: View {
    let request: AgentInputRequest
    let enabled: Bool
    let selectedOptionId: String?
    let onOptionSelected: (AgentInputRequest.Option) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            let options = visibleOptions
            let postOptions = options.filter { $0.postPreview != nil }
            let userOptions = options.filter { $0.userPreview != nil }
            let actionOptions = options.filter { $0.postPreview == nil && $0.userPreview == nil }

            ForEach(postOptions, id: \.id) { option in
                if let post = option.postPreview {
                    Button {
                        if enabled {
                            onOptionSelected(option)
                        }
                    } label: {
                        StatusInsightPostPreview(
                            post: post,
                            onClick: {
                                onOptionSelected(option)
                            }
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(!enabled)
                }
            }

            ForEach(userOptions, id: \.id) { option in
                if let user = option.userPreview {
                    Button {
                        if enabled {
                            onOptionSelected(option)
                        }
                    } label: {
                        AgentUserPreview(
                            user: user,
                            onClick: {
                                onOptionSelected(option)
                            }
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(!enabled)
                }
            }

            AgentRequestPreviewView(request: request)

            if !actionOptions.isEmpty {
                HStack(spacing: 8) {
                    ForEach(actionOptions, id: \.id) { option in
                        AgentActionOptionButton(
                            option: option,
                            enabled: enabled,
                            onOptionSelected: onOptionSelected
                        )
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var visibleOptions: [AgentInputRequest.Option] {
        let options = Array(request.options)
        guard let selectedOptionId else {
            return options
        }
        return options.filter { $0.id == selectedOptionId }
    }
}

private struct AgentRequestPreviewView: View {
    let request: AgentInputRequest

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let previewPost = request.postPreview {
                StatusInsightPostPreview(post: previewPost, onClick: nil)
            }

            if let previewUser = request.userPreview {
                AgentUserPreview(user: previewUser, onClick: {})
            }
        }
    }
}

private struct AgentActionOptionButton: View {
    let option: AgentInputRequest.Option
    let enabled: Bool
    let onOptionSelected: (AgentInputRequest.Option) -> Void

    var body: some View {
        Button(role: option.buttonRole) {
            if enabled {
                onOptionSelected(option)
            }
        } label: {
            Text(option.label)
                .frame(maxWidth: .infinity)
        }
        .modifier(AgentActionOptionButtonStyle(isPrimary: option.buttonType == .primary))
        .frame(maxWidth: .infinity)
        .disabled(!enabled)
    }
}

private struct AgentActionOptionButtonStyle: ViewModifier {
    let isPrimary: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if isPrimary {
            content.buttonStyle(.borderedProminent)
        } else {
            content.buttonStyle(.bordered)
        }
    }
}

private extension AgentInputRequest.Option {
    var buttonRole: ButtonRole? {
        switch buttonType {
        case .destructive:
            return .destructive
        case .cancel:
            return .cancel
        default:
            return nil
        }
    }
}

private struct AgentUserPreview: View {
    let user: UiProfile
    let onClick: () -> Void

    var body: some View {
        UserCompatView(
            data: user,
            trailing: { EmptyView() },
            onClicked: onClick
        )
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.flareSecondarySystemGroupedBackground, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.flareSeparator.opacity(0.55), lineWidth: 1)
        )
    }
}

public struct StatusInsightPostPreview: View {
    @Environment(\.timelineAppearance) private var timelineAppearance
    let post: UiTimelineV2.Post
    let onClick: (() -> Void)?

    public init(post: UiTimelineV2.Post, onClick: (() -> Void)? = nil) {
        self.post = post
        self.onClick = onClick
    }

    public var body: some View {
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
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.flareSeparator, lineWidth: 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .onTapGesture {
            onClick?()
        }
    }
}

public struct ProfileInsightUserPreview: View {
    let profile: UiProfile
    let onClick: (() -> Void)?

    public init(profile: UiProfile, onClick: (() -> Void)? = nil) {
        self.profile = profile
        self.onClick = onClick
    }

    public var body: some View {
        UserCompatView(data: profile)
            .padding(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Color.flareSeparator, lineWidth: 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .onTapGesture {
                onClick?()
            }
    }
}

public struct StatusInsightCurrentTrace: View {
    let trace: String

    public init(trace: String) {
        self.trace = trace
    }

    public var body: some View {
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

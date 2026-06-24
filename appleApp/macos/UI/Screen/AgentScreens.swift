import FlareAppleCore
import FlareAppleUI
import Foundation
@preconcurrency import KotlinSharedUI
import SwiftUI

struct AgentChatHistoryScreen: View {
    @ObservedObject private var windowCoordinator: MacAgentWindowCoordinator
    @StateObject private var presenter = KotlinPresenter(presenter: AgentChatHistoryPresenter())
    @State private var selectedConversationId: String?
    @State private var activeDetailRoute: Route?
    @State private var activeDetailConversationId: String?
    let onNavigate: (Route) -> Void

    init(
        windowCoordinator: MacAgentWindowCoordinator = .shared,
        onNavigate: @escaping (Route) -> Void = { _ in }
    ) {
        self.windowCoordinator = windowCoordinator
        self.onNavigate = onNavigate
    }

    var body: some View {
        NavigationSplitView {
            List(selection: $selectedConversationId) {
                ForEach(rooms, id: \.id) { room in
                    AgentChatHistoryRow(room: room)
                        .tag(room.id)
                }
            }
            .overlay {
                if rooms.isEmpty {
                    ContentUnavailableView {
                        Label {
                            Text("agent_history_empty", bundle: .main)
                        } icon: {
                            Image(fontAwesome: .robot)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .listStyle(.sidebar)
            .navigationTitle("agent_history_title")
            .navigationSplitViewColumnWidth(min: 220, ideal: 280, max: 360)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        createNewConversation()
                    } label: {
                        Image(fontAwesome: .plus)
                    }
                    .help(String(localized: "agent_chat_title", bundle: .main))
                    .accessibilityLabel(Text("agent_chat_title", bundle: .main))
                }
            }
        } detail: {
            if let activeDetailRoute,
               selectedConversationId == activeDetailConversationId {
                activeDetailRoute.view(
                    onNavigate: onNavigate,
                    goBack: {}
                )
                .id(activeDetailConversationId)
            } else if let selectedConversationId {
                AgentChatScreen(
                    conversationId: selectedConversationId,
                    initialMessage: nil,
                    onNavigate: onNavigate
                )
                .id(selectedConversationId)
            } else {
                AgentChatHistoryDetailPlaceholder {
                    createNewConversation()
                }
            }
        }
        .onAppear(perform: reconcileSelection)
        .onAppear {
            if let request = windowCoordinator.request {
                openAgentRoute(request.route)
            }
        }
        .onChange(of: roomIds) { _, _ in
            reconcileSelection()
        }
        .onChange(of: selectedConversationId) { _, newValue in
            if newValue != activeDetailConversationId {
                activeDetailRoute = nil
                activeDetailConversationId = nil
            }
        }
        .onChange(of: windowCoordinator.request?.id) { _, _ in
            if let request = windowCoordinator.request {
                openAgentRoute(request.route)
            }
        }
    }

    private var rooms: [AgentChatRoom] {
        Array(presenter.state.rooms)
    }

    private var roomIds: [String] {
        rooms.map(\.id)
    }

    private func createNewConversation() {
        openAgentRoute(.agentChat(Route.newGenericChatConversationId(), nil))
    }

    private func reconcileSelection() {
        if let selectedConversationId,
           roomIds.contains(selectedConversationId) || selectedConversationId.isPendingAgentConversationId {
            return
        }

        selectedConversationId = roomIds.first
    }

    private func openAgentRoute(_ route: Route) {
        guard route.isAgentWindowRoute else {
            return
        }

        if route == .agentHistory {
            activeDetailRoute = nil
            activeDetailConversationId = nil
            reconcileSelection()
            return
        }

        guard let conversationId = route.agentConversationId else {
            return
        }

        activeDetailConversationId = conversationId
        activeDetailRoute = route
        selectedConversationId = conversationId
    }
}

private struct AgentChatHistoryRow: View {
    let room: AgentChatRoom

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(verbatim: room.title)
                .font(.headline)
                .lineLimit(2)

            HStack(spacing: 6) {
                if room.isRunning {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .scaleEffect(0.5)
                }

                TimelineView(.periodic(from: .now, by: 60)) { context in
                    Text(relativeUpdatedAtText(now: context.date))
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }

    private var updatedAtDate: Date {
        Date(timeIntervalSince1970: TimeInterval(room.updatedAt) / 1000)
    }

    private func relativeUpdatedAtText(now: Date) -> String {
        let elapsedSeconds = max(60, Int(now.timeIntervalSince(updatedAtDate)))
        let formatter = DateComponentsFormatter()
        formatter.allowedUnits = Self.allowedUnits(for: elapsedSeconds)
        formatter.maximumUnitCount = 1
        formatter.unitsStyle = .abbreviated
        return formatter.string(from: TimeInterval(elapsedSeconds)) ?? ""
    }

    private static func allowedUnits(for elapsedSeconds: Int) -> NSCalendar.Unit {
        switch elapsedSeconds {
        case ..<3_600:
            return .minute
        case ..<86_400:
            return .hour
        case ..<604_800:
            return .day
        case ..<2_592_000:
            return .weekOfMonth
        case ..<31_536_000:
            return .month
        default:
            return .year
        }
    }
}

private struct AgentChatHistoryDetailPlaceholder: View {
    let onNewChat: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label {
                Text("settings_agent_history_title", bundle: .main)
            } icon: {
                Image(fontAwesome: .robot)
            }
        } description: {
            Text("macos_placeholder_agent_history", bundle: .main)
        } actions: {
            Button(action: onNewChat) {
                Label {
                    Text("agent_chat_title", bundle: .main)
                } icon: {
                    Image(fontAwesome: .plus)
                }
            }
            .buttonStyle(.borderedProminent)
        }
    }
}

private extension String {
    var isPendingAgentConversationId: Bool {
        hasPrefix("generic-chat:") ||
            hasPrefix("local-history:") ||
            hasPrefix("status-insight:") ||
            hasPrefix("profile-insight:")
    }
}

struct AgentChatScreen: View {
    @StateObject private var presenter: KotlinPresenter<GenericChatPresenterState>
    let onNavigate: (Route) -> Void

    var body: some View {
        AgentChatView(
            messages: Array(presenter.state.messages),
            isRunning: presenter.state.room.isRunning,
            canSend: presenter.state.canSend,
            errorMessage: presenter.state.room.errorMessage,
            runningTrace: presenter.state.room.currentTrace?.localizedLabel ?? String(localized: "agent_chat_thinking", bundle: .main),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder", bundle: .main),
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
            },
            leadingContent: {
                AnyView(
                    ForEach(Array(presenter.state.statusInsightPosts.enumerated()), id: \.offset) { _, post in
                        StatusInsightPostPreview(
                            post: post,
                            onClick: {
                                onNavigate(.statusDetail(post.accountType, post.statusKey))
                            }
                        )
                    }
                )
            }
        )
        .navigationTitle(presenter.state.room.title.isEmpty ? String(localized: "agent_chat_title", bundle: .main) : presenter.state.room.title)
    }
}

extension AgentChatScreen {
    init(
        conversationId: String,
        initialMessage: String?,
        onNavigate: @escaping (Route) -> Void = { _ in }
    ) {
        let normalizedInitialMessage = initialMessage?.trimmingCharacters(in: .whitespacesAndNewlines)
        self.onNavigate = onNavigate
        self._presenter = .init(
            wrappedValue: .init(
                presenter: GenericChatPresenter(
                    conversationId: conversationId,
                    initialMessage: normalizedInitialMessage?.isEmpty == false ? normalizedInitialMessage : nil
                )
            )
        )
    }
}

struct LocalHistoryAgentScreen: View {
    @StateObject private var presenter: KotlinPresenter<LocalHistoryAgentPresenterState>
    let onNavigate: (Route) -> Void

    var body: some View {
        AgentChatView(
            messages: Array(presenter.state.messages),
            isRunning: presenter.state.room.isRunning,
            canSend: presenter.state.canSend,
            errorMessage: presenter.state.room.errorMessage,
            runningTrace: presenter.state.room.currentTrace?.localizedLabel ?? String(localized: "agent_chat_thinking", bundle: .main),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder", bundle: .main),
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
        .navigationTitle(String(localized: "local_history_title", bundle: .main))
    }
}

extension LocalHistoryAgentScreen {
    init(
        conversationId: String,
        query: String?,
        target: String,
        onNavigate: @escaping (Route) -> Void = { _ in }
    ) {
        let normalizedQuery = query?.trimmingCharacters(in: .whitespacesAndNewlines)
        self.onNavigate = onNavigate
        self._presenter = .init(
            wrappedValue: .init(
                presenter: LocalHistoryAgentPresenter(
                    conversationId: conversationId,
                    query: normalizedQuery?.isEmpty == false ? normalizedQuery : nil,
                    target: LocalHistoryAgentTarget.companion.fromRouteValue(value: target)
                )
            )
        )
    }
}

struct StatusInsightScreen: View {
    @StateObject private var presenter: KotlinPresenter<StatusInsightPresenterState>
    let onNavigate: (Route) -> Void

    var body: some View {
        AgentChatView(
            messages: Array(presenter.state.messages),
            isRunning: presenter.state.room.isRunning,
            canSend: presenter.state.canSend,
            errorMessage: presenter.state.room.errorMessage,
            runningTrace: presenter.state.room.currentTrace?.localizedLabel ?? String(localized: "status_insight_analyzing", bundle: .main),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder", bundle: .main),
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
        .navigationTitle(String(localized: "status_insight_title", bundle: .main))
    }
}

extension StatusInsightScreen {
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

struct ProfileInsightScreen: View {
    @StateObject private var presenter: KotlinPresenter<ProfileInsightPresenterState>
    let accountType: AccountType
    let onNavigate: (Route) -> Void

    var body: some View {
        AgentChatView(
            messages: Array(presenter.state.messages),
            isRunning: presenter.state.room.isRunning,
            canSend: presenter.state.canSend,
            errorMessage: presenter.state.room.errorMessage,
            runningTrace: presenter.state.room.currentTrace?.localizedLabel ?? String(localized: "profile_insight_analyzing", defaultValue: "Analyzing this profile...", bundle: .main),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder", bundle: .main),
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
        .navigationTitle(String(localized: "profile_insight_title", defaultValue: "Profile insight", bundle: .main))
    }
}

extension ProfileInsightScreen {
    init(
        accountType: AccountType,
        userKey: MicroBlogKey,
        onNavigate: @escaping (Route) -> Void = { _ in }
    ) {
        self.accountType = accountType
        self.onNavigate = onNavigate
        self._presenter = .init(
            wrappedValue: .init(
                presenter: ProfileInsightPresenter(
                    accountType: accountType,
                    userKey: userKey
                )
            )
        )
    }
}

struct AgentChatView: View {
    let messages: [AgentChatHistoryMessage]
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
    private let leadingContent: () -> AnyView

    @State private var draft = ""

    init(
        messages: [AgentChatHistoryMessage],
        isRunning: Bool,
        canSend: Bool,
        errorMessage: String?,
        runningTrace: String,
        inputPlaceholder: String,
        onInputChange: @escaping (String) -> Void,
        onSend: @escaping () -> Void,
        onInputRequestOptionSelected: @escaping (AgentInputRequest.Option) -> Void = { _ in },
        onPostClick: @escaping (UiTimelineV2.Post) -> Void = { _ in },
        onUserClick: @escaping (UiProfile) -> Void = { _ in },
        leadingContent: @escaping () -> AnyView = { AnyView(EmptyView()) }
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
        self.leadingContent = leadingContent
    }

    var body: some View {
        VStack(spacing: 0) {
            Divider()
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 10) {
                        leadingContent()
                            .frame(maxWidth: .infinity, alignment: .leading)

                        ForEach(messages, id: \.id) { message in
                            AgentChatMessageBubble(
                                parts: Array(message.parts),
                                isUser: message.isUser,
                                onInputRequestOptionSelected: onInputRequestOptionSelected,
                                onPostClick: onPostClick,
                                onUserClick: onUserClick
                            )
                            .id(message.id)
                        }

                        if isRunning {
                            StatusInsightCurrentTrace(trace: runningTrace)
                                .id("agent-running")
                        }

                        if let errorMessage {
                            Text(verbatim: errorMessage)
                                .foregroundStyle(.red)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }

                        Color.clear
                            .frame(height: 1)
                            .id("agent-bottom")
                    }
                    .padding()
                }
                .onChange(of: messages.count) { _, _ in
                    scrollToBottom(proxy: proxy)
                }
                .onChange(of: isRunning) { _, _ in
                    scrollToBottom(proxy: proxy)
                }
                .onAppear {
                    scrollToBottom(proxy: proxy)
                }
            }
            Divider()
            AgentChatInputBar(
                draft: $draft,
                inputPlaceholder: inputPlaceholder,
                canSend: canSend,
                onInputChange: onInputChange,
                onSend: submit
            )
        }
        .background(Color.flareSystemGroupedBackground)
    }

    private func scrollToBottom(proxy: ScrollViewProxy) {
        DispatchQueue.main.async {
            withAnimation(.easeOut(duration: 0.18)) {
                proxy.scrollTo("agent-bottom", anchor: .bottom)
            }
        }
    }

    private func submit() {
        guard canSend else { return }
        onSend()
        draft = ""
    }
}

private struct AgentChatInputBar: View {
    @Binding var draft: String
    let inputPlaceholder: String
    let canSend: Bool
    let onInputChange: (String) -> Void
    let onSend: () -> Void

    var body: some View {
        HStack(alignment: .bottom, spacing: 10) {
            TextField(inputPlaceholder, text: $draft, axis: .vertical)
                .lineLimit(1...5)
                .textFieldStyle(.roundedBorder)
                .onSubmit {
                    onSend()
                }

            Button(action: onSend) {
                Image(systemName: "paperplane.fill")
                    .frame(width: 28, height: 22)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!canSend)
            .help(String(localized: "agent_chat_send", bundle: .main))
            .accessibilityLabel(Text("agent_chat_send", bundle: .main))
        }
        .padding()
        .background(.bar)
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

    private func markdownText(_ value: String) -> Text {
        if isUser {
            Text(verbatim: value)
        } else if let attributedText = try? AttributedString(
            markdown: value,
            options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        ) {
            Text(attributedText)
        } else {
            Text(verbatim: value)
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
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.flareSeparator, lineWidth: 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .onTapGesture {
            onClick?()
        }
    }
}

struct ProfileInsightUserPreview: View {
    let profile: UiProfile
    let onClick: (() -> Void)?

    init(profile: UiProfile, onClick: (() -> Void)? = nil) {
        self.profile = profile
        self.onClick = onClick
    }

    var body: some View {
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

private func localizedAgentPresentationString(
    _ key: String,
    fallback: String,
    arguments: [String] = []
) -> String {
    let value = Bundle.main.localizedString(forKey: key, value: fallback, table: nil)
    guard !arguments.isEmpty else { return value }
    return String(format: value, arguments: arguments.map { $0 as CVarArg })
}

extension AgentTrace {
    var localizedLabel: String {
        if let toolKey {
            return toolKey.localizedLabel
        }

        return switch phase {
        case .loadingPostContext:
            localizedAgentPresentationString("status_insight_trace_loading_post_context", fallback: "Loading post context")
        case .postContextLoaded:
            localizedAgentPresentationString("status_insight_trace_post_context_loaded", fallback: "Post context loaded")
        case .preparingImages:
            localizedAgentPresentationString("status_insight_trace_preparing_images", fallback: "Preparing images")
        case .imagesUnsupportedFallback:
            localizedAgentPresentationString("status_insight_trace_images_unsupported_fallback", fallback: "Images are not supported, using text fallback")
        case .agentStarted:
            localizedAgentPresentationString("status_insight_trace_agent_started", fallback: "Agent started")
        case .strategyStarted:
            localizedAgentPresentationString("status_insight_trace_strategy_started", fallback: "Strategy started")
        case .strategyCompleted:
            localizedAgentPresentationString("status_insight_trace_strategy_completed", fallback: "Strategy completed")
        case .subgraphStarted:
            localizedAgentPresentationString("status_insight_trace_subgraph_started", fallback: "Subgraph started")
        case .subgraphCompleted:
            localizedAgentPresentationString("status_insight_trace_subgraph_completed", fallback: "Subgraph completed")
        case .subgraphFailed:
            localizedAgentPresentationString("status_insight_trace_subgraph_failed", fallback: "Subgraph failed")
        case .askingModel:
            localizedAgentPresentationString("status_insight_trace_asking_model", fallback: "Asking model %@", arguments: [detail ?? ""])
        case .modelResponseReceived:
            localizedAgentPresentationString("status_insight_trace_model_response_received", fallback: "Model response received")
        case .streamingStarted:
            localizedAgentPresentationString("status_insight_trace_streaming_started", fallback: "Streaming started %@", arguments: [detail ?? ""])
        case .streamingResponse:
            localizedAgentPresentationString("status_insight_trace_streaming_response", fallback: "Streaming response")
        case .streamingCompleted:
            localizedAgentPresentationString("status_insight_trace_streaming_completed", fallback: "Streaming completed")
        case .streamingFailed:
            localizedAgentPresentationString("status_insight_trace_streaming_failed", fallback: "Streaming failed")
        case .runningStep:
            localizedAgentPresentationString("status_insight_trace_running_step", fallback: "Running step")
        case .stepCompleted:
            localizedAgentPresentationString("status_insight_trace_step_completed", fallback: "Step completed")
        case .stepFailed:
            localizedAgentPresentationString("status_insight_trace_step_failed", fallback: "Step failed")
        case .toolCallStarted:
            detail ?? localizedAgentPresentationString("status_insight_trace_running_step", fallback: "Running step")
        case .toolCallCompleted:
            detail ?? localizedAgentPresentationString("status_insight_trace_step_completed", fallback: "Step completed")
        case .toolValidationFailed:
            detail ?? localizedAgentPresentationString("status_insight_trace_tool_validation_failed", fallback: "Tool validation failed")
        case .toolCallFailed:
            detail ?? localizedAgentPresentationString("status_insight_trace_tool_call_failed", fallback: "Tool call failed")
        case .agentCompleted:
            localizedAgentPresentationString("status_insight_trace_agent_completed", fallback: "Agent completed")
        case .agentFailed:
            localizedAgentPresentationString("status_insight_trace_agent_failed", fallback: "Agent failed")
        case .agentClosing:
            localizedAgentPresentationString("status_insight_trace_agent_closing", fallback: "Agent closing")
        }
    }
}

private extension AgentToolKey {
    var localizedLabel: String {
        return switch self {
        case .loadStatusContextStarted:
            localizedAgentPresentationString("status_insight_trace_tool_load_status_context_started", fallback: "Loading status context")
        case .loadStatusContextCompleted:
            localizedAgentPresentationString("status_insight_trace_tool_load_status_context_completed", fallback: "Loaded status context")
        case .loadStatusContextValidationFailed:
            localizedAgentPresentationString("status_insight_trace_tool_load_status_context_validation_failed", fallback: "Status context validation failed")
        case .loadStatusContextFailed:
            localizedAgentPresentationString("status_insight_trace_tool_load_status_context_failed", fallback: "Failed to load status context")
        case .searchPostsStarted, .searchUsersStarted:
            localizedAgentPresentationString("status_insight_trace_tool_search_status_started", fallback: "Searching statuses")
        case .searchPostsCompleted, .searchUsersCompleted:
            localizedAgentPresentationString("status_insight_trace_tool_search_status_completed", fallback: "Search completed")
        case .searchPostsValidationFailed, .searchUsersValidationFailed:
            localizedAgentPresentationString("status_insight_trace_tool_search_status_validation_failed", fallback: "Search validation failed")
        case .searchPostsFailed, .searchUsersFailed:
            localizedAgentPresentationString("status_insight_trace_tool_search_status_failed", fallback: "Search failed")
        }
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

private func agentRoute(for user: UiProfile) -> Route? {
    guard let event = user.clickEvent as? ClickEventDeeplink else {
        return nil
    }
    return Route.fromDeepLink(url: event.url)
}

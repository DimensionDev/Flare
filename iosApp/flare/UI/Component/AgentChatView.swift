import ChatLayout
import SwiftUI
import SwiftUIBackports
import KotlinSharedUI

struct AgentChatView<Message>: View {
    let messages: [Message]
    let input: String
    let isRunning: Bool
    let canSend: Bool
    let error: KotlinThrowable?
    let runningTrace: String
    let inputRequest: AgentInputRequest?
    let inputPlaceholder: String
    let messageText: (Message) -> String
    let messageLocalizedText: (Message) -> AgentLocalizedText?
    let messageParts: (Message) -> [AgentMessagePart]
    let messageInputRequest: (Message) -> AgentInputRequest?
    let messageInputRequestSelected: (Message) -> Bool
    let messageInputRequestSelectedOptionId: (Message) -> String?
    let isUserMessage: (Message) -> Bool
    let onInputChange: (String) -> Void
    let onSend: () -> Void
    let onInputRequestOptionSelected: (AgentInputRequest.Option) -> Void
    let onPostClick: (UiTimelineV2.Post) -> Void
    let onUserClick: (UiProfile) -> Void
    private let leadingContent: () -> AnyView

    @State private var draft: String = ""
    @State private var inputBarHeight: CGFloat = 0

    init(
        messages: [Message],
        input: String,
        isRunning: Bool,
        canSend: Bool,
        error: KotlinThrowable?,
        runningTrace: String,
        inputRequest: AgentInputRequest? = nil,
        inputPlaceholder: String,
        messageText: @escaping (Message) -> String,
        messageLocalizedText: @escaping (Message) -> AgentLocalizedText? = { _ in nil },
        messageParts: @escaping (Message) -> [AgentMessagePart],
        messageInputRequest: @escaping (Message) -> AgentInputRequest? = { _ in nil },
        messageInputRequestSelected: @escaping (Message) -> Bool = { _ in false },
        messageInputRequestSelectedOptionId: @escaping (Message) -> String? = { _ in nil },
        isUserMessage: @escaping (Message) -> Bool,
        onInputChange: @escaping (String) -> Void,
        onSend: @escaping () -> Void,
        onInputRequestOptionSelected: @escaping (AgentInputRequest.Option) -> Void = { _ in },
        onPostClick: @escaping (UiTimelineV2.Post) -> Void = { _ in },
        onUserClick: @escaping (UiProfile) -> Void = { _ in },
        leadingContent: @escaping () -> AnyView = { AnyView(EmptyView()) }
    ) {
        self.messages = messages
        self.input = input
        self.isRunning = isRunning
        self.canSend = canSend
        self.error = error
        self.runningTrace = runningTrace
        self.inputRequest = inputRequest
        self.inputPlaceholder = inputPlaceholder
        self.messageText = messageText
        self.messageLocalizedText = messageLocalizedText
        self.messageParts = messageParts
        self.messageInputRequest = messageInputRequest
        self.messageInputRequestSelected = messageInputRequestSelected
        self.messageInputRequestSelectedOptionId = messageInputRequestSelectedOptionId
        self.isUserMessage = isUserMessage
        self.onInputChange = onInputChange
        self.onSend = onSend
        self.onInputRequestOptionSelected = onInputRequestOptionSelected
        self.onPostClick = onPostClick
        self.onUserClick = onUserClick
        self.leadingContent = leadingContent
    }

    var body: some View {
        ZStack {
            Color(.systemGroupedBackground)
                .ignoresSafeArea()

            AgentChatMessagesView(rows: rows, bottomAccessoryInset: inputBarHeight)
                .ignoresSafeArea(.container, edges: .vertical)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            HStack(alignment: .center, spacing: 10) {
                TextField(inputRequest?.localizedFreeTextPlaceholder.localizedAgentText ?? inputPlaceholder, text: $draft, axis: .vertical)
                    .lineLimit(1...4)
                    .submitLabel(.send)
                    .onSubmit {
                        submit()
                    }
                    .padding()
                    .backport
                    .glassEffect(.regularInteractive, in: .capsule, fallbackBackground: .regularMaterial)

                Button {
                    submit()
                } label: {
                    Image(systemName: "paperplane.fill")
                        .font(.title2)
                        .frame(width: 48, height: 48)
                }
                .backport
                .glassProminentButtonStyle()
                .disabled(!canSend)
            }
            .frame(maxWidth: .infinity)
            .padding([.horizontal, .bottom])
            .background {
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: AgentChatInputBarHeightPreferenceKey.self,
                        value: proxy.size.height
                    )
                }
            }
        }
        .onPreferenceChange(AgentChatInputBarHeightPreferenceKey.self) { height in
            inputBarHeight = height
        }
        .onAppear {
            draft = input
        }
        .onChange(of: input) { _, value in
            if draft != value {
                draft = value
            }
        }
        .onChange(of: draft) { _, value in
            onInputChange(value)
        }
    }

    private var rows: [AgentChatRow] {
        var result: [AgentChatRow] = [
            .leading(content: leadingContent())
        ]

        result.append(
            contentsOf: messages.enumerated().map { index, message in
                let localizedText = messageLocalizedText(message)?.localizedAgentText
                let text = localizedText ?? messageText(message)
                return .message(
                    id: "message-\(index)",
                    text: text,
                    parts: localizedText == nil ? messageParts(message) : [],
                    inputRequest: messageInputRequest(message),
                    inputRequestSelected: messageInputRequestSelected(message),
                    inputRequestSelectedOptionId: messageInputRequestSelectedOptionId(message),
                    isUser: isUserMessage(message),
                    onInputRequestOptionSelected: onInputRequestOptionSelected,
                    onPostClick: onPostClick,
                    onUserClick: onUserClick
                )
            }
        )

        if isRunning {
            result.append(.running(text: runningTrace))
        }

        if let error {
            result.append(.error(text: error.message ?? String(localized: "status_insight_error")))
        }

        return result
    }

    private func submit() {
        guard canSend else {
            return
        }
        onSend()
        draft = ""
    }
}

private extension Optional where Wrapped == AgentLocalizedText {
    var localizedAgentText: String? {
        self?.localizedAgentText
    }
}

private extension AgentLocalizedText {
    var localizedAgentText: String {
        func arg(_ index: Int) -> String {
            guard index < args.count else {
                return ""
            }
            return args[index]
        }
        func composeConfirmationTitle(_ keyName: String) -> String {
            switch keyName {
            case "ComposeReplyConfirmationTitle":
                return String(localized: "agent_ui_compose_reply_confirmation_title", defaultValue: "Reply to this post with the selected account?")
            case "ComposeQuoteConfirmationTitle":
                return String(localized: "agent_ui_compose_quote_confirmation_title", defaultValue: "Quote this post with the selected account?")
            default:
                return String(localized: "agent_ui_compose_send_confirmation_title", defaultValue: "Send this content with the selected account?")
            }
        }

        switch key.name {
        case "DynamicText":
            return arg(0)
        case "Cancel":
            return String(localized: "agent_ui_cancel", defaultValue: "Cancel")
        case "ConfirmExecute":
            return String(localized: "agent_ui_confirm_execute", defaultValue: "Confirm")
        case "ConfirmSaveSubscription":
            return String(localized: "agent_ui_confirm_save_subscription", defaultValue: "Save")
        case "CancelSaveSubscription":
            return String(localized: "agent_ui_cancel_save_subscription", defaultValue: "Cancel save")
        case "ConfirmDeleteSubscription":
            return String(localized: "agent_ui_confirm_delete_subscription", defaultValue: "Delete")
        case "CancelDeleteSubscription":
            return String(localized: "agent_ui_cancel_delete_subscription", defaultValue: "Cancel delete")
        case "ConfirmSendPost":
            return String(localized: "agent_ui_confirm_send_post", defaultValue: "Send")
        case "CancelSendPost":
            return String(localized: "agent_ui_cancel_send_post", defaultValue: "Cancel send")
        case "SelectLoadSubscriptionSource":
            return String(localized: "agent_ui_select_load_subscription_source", defaultValue: "Select a subscription source to load.")
        case "SelectDeleteSubscriptionSource":
            return String(localized: "agent_ui_select_delete_subscription_source", defaultValue: "Select a subscription source to delete.")
        case "SelectSaveSubscriptionSource":
            return String(localized: "agent_ui_select_save_subscription_source", defaultValue: "Select a subscription source to save.")
        case "SubscriptionSourcePlaceholder":
            return String(localized: "agent_ui_subscription_source_placeholder", defaultValue: "You can also enter a subscription URL, host, or type…")
        case "SubscriptionSaveSelectionPlaceholder":
            return String(localized: "agent_ui_subscription_save_selection_placeholder", defaultValue: "You can also enter the subscription source or type to save…")
        case "SubscriptionSaveConfirmationPlaceholder":
            return String(localized: "agent_ui_subscription_save_confirmation_placeholder", defaultValue: "You can also enter a new source, type, or title…")
        case "SubscriptionDeleteConfirmationPlaceholder":
            return String(localized: "agent_ui_subscription_delete_confirmation_placeholder", defaultValue: "You can also enter another source or cancel…")
        case "SubscriptionSaveConfirmationMessage":
            return String(
                format: String(localized: "agent_ui_subscription_save_confirmation_message", defaultValue: "Save this subscription source?\n\nType: %@ (%@)\nURL/Host: %@\nTitle: %@\nIcon: %@\nRSS display mode: %@\n\nConfirm to save."),
                arg(0), arg(1), arg(2), arg(3), arg(4), arg(5)
            )
        case "SubscriptionDeleteConfirmationMessage":
            return String(
                format: String(localized: "agent_ui_subscription_delete_confirmation_message", defaultValue: "Delete this subscription source?\n\nsourceId: %@\nType: %@ (%@)\nURL/Host: %@\nTitle: %@\n\nConfirm to delete."),
                arg(0), arg(1), arg(2), arg(3), arg(4)
            )
        case "SelectComposeTargetPost":
            return String(format: String(localized: "agent_ui_select_compose_target_post", defaultValue: "Select the post to %@."), arg(0))
        case "SelectComposeAccount":
            return String(format: String(localized: "agent_ui_select_compose_account", defaultValue: "Select the account to %@ this content."), arg(0))
        case "SelectComposePlatform":
            return String(format: String(localized: "agent_ui_select_compose_platform", defaultValue: "Select the platform to %@ this content."), arg(0))
        case "ComposeTargetPostPlaceholder":
            return String(localized: "agent_ui_compose_target_post_placeholder", defaultValue: "You can also enter a target post link, statusKey, or revised content…")
        case "ComposeAccountPlaceholder":
            return String(localized: "agent_ui_compose_account_placeholder", defaultValue: "You can also enter an account or revised content…")
        case "ComposePlatformPlaceholder":
            return String(localized: "agent_ui_compose_platform_placeholder", defaultValue: "You can also enter a platform, account, or revised content…")
        case "ComposeConfirmationPlaceholder":
            return String(localized: "agent_ui_compose_confirmation_placeholder", defaultValue: "You can also enter revised content…")
        case "ComposeSendConfirmationTitle":
            return String(localized: "agent_ui_compose_send_confirmation_title", defaultValue: "Send this content with the selected account?")
        case "ComposeReplyConfirmationTitle":
            return String(localized: "agent_ui_compose_reply_confirmation_title", defaultValue: "Reply to this post with the selected account?")
        case "ComposeQuoteConfirmationTitle":
            return String(localized: "agent_ui_compose_quote_confirmation_title", defaultValue: "Quote this post with the selected account?")
        case "ComposeConfirmationMessage":
            return String(
                format: String(localized: "agent_ui_compose_confirmation_message", defaultValue: "%@\n\nAccount: %@\nPlatform: %@\n\n%@"),
                composeConfirmationTitle(arg(0)), arg(1), arg(4), arg(12)
            )
        case "SelectPostActionPost":
            return String(localized: "agent_ui_select_post_action_post", defaultValue: "Select a post to operate on.")
        case "SelectPostAction":
            return String(localized: "agent_ui_select_post_action", defaultValue: "Select an action for this post.")
        case "PostActionTargetPostPlaceholder":
            return String(localized: "agent_ui_post_action_target_post_placeholder", defaultValue: "You can also enter a target post attachmentRef or statusKey…")
        case "PostActionPlaceholder":
            return String(localized: "agent_ui_post_action_placeholder", defaultValue: "You can also enter the action to perform…")
        case "PostActionConfirmationPlaceholder":
            return String(localized: "agent_ui_post_action_confirmation_placeholder", defaultValue: "You can also enter another action or cancel…")
        case "PostActionConfirmationMessage":
            return String(
                format: String(localized: "agent_ui_post_action_confirmation_message", defaultValue: "Confirm %@ for this post?\n\nAccount: %@\nTarget post: %@\nAuthor: %@\nSummary: %@\n\nConfirm to continue."),
                arg(0), arg(1), arg(2), arg(3), arg(4)
            )
        case "SelectRelationStateUser":
            return String(localized: "agent_ui_select_relation_state_user", defaultValue: "Select a user to inspect relationship status.")
        case "SelectRelationUser":
            return String(localized: "agent_ui_select_relation_user", defaultValue: "Select a user to operate on.")
        case "SelectRelationAction":
            return String(localized: "agent_ui_select_relation_action", defaultValue: "Select a relationship action for this user.")
        case "SelectRelationAccount":
            return String(localized: "agent_ui_select_relation_account", defaultValue: "Select the account to use for this relationship action.")
        case "RelationUserPlaceholder":
            return String(localized: "agent_ui_relation_user_placeholder", defaultValue: "You can also enter a user link, handle, or userKey…")
        case "RelationActionPlaceholder":
            return String(localized: "agent_ui_relation_action_placeholder", defaultValue: "You can also enter the relationship action…")
        case "RelationAccountPlaceholder":
            return String(localized: "agent_ui_relation_account_placeholder", defaultValue: "You can also enter an account, platform, or revised action…")
        case "RelationConfirmationPlaceholder":
            return String(localized: "agent_ui_relation_confirmation_placeholder", defaultValue: "You can also enter another action or cancel…")
        case "RelationConfirmationMessage":
            return String(
                format: String(localized: "agent_ui_relation_confirmation_message", defaultValue: "Confirm %@ for this user?\n\nAccount: %@\nPlatform: %@\nTarget user: %@\nDisplay name: %@\nHandle: %@\n\nConfirm to continue."),
                arg(0), arg(1), arg(2), arg(3), arg(4), arg(5)
            )
        case "SelectRecentPostsUser":
            return String(localized: "agent_ui_select_recent_posts_user", defaultValue: "Select a user to view recent posts.")
        case "SelectMatchedUser":
            return String(localized: "agent_ui_select_matched_user", defaultValue: "Multiple matching users were found. Select one to continue.")
        case "SelectProfileUser":
            return String(localized: "agent_ui_select_profile_user", defaultValue: "Select a user to view their profile.")
        case "SelectFollowingUser":
            return String(localized: "agent_ui_select_following_user", defaultValue: "Select a user to view who they follow.")
        case "SelectFollowersUser":
            return String(localized: "agent_ui_select_followers_user", defaultValue: "Select a user to view their followers.")
        case "SelectProfileTabsUser":
            return String(localized: "agent_ui_select_profile_tabs_user", defaultValue: "Select a user to view profile tabs.")
        case "StatusInsightUserPlaceholder":
            return String(localized: "agent_ui_status_insight_user_placeholder", defaultValue: "You can also enter a user link, handle, or userKey…")
        default:
            return ""
        }
    }
}

private struct AgentChatInputBarHeightPreferenceKey: PreferenceKey {
    static let defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
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
            let confirmOption = actionOptions.first { $0.id == "confirm" }

            if let confirmOption {
                AgentConfirmationRequestView(
                    request: request,
                    actionOptions: actionOptions,
                    confirmOption: confirmOption,
                    enabled: enabled,
                    onOptionSelected: onOptionSelected
                )
            } else {
                Text(request.localizedPrompt.localizedAgentText)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)

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
                            UserCompatView(
                                data: user,
                                trailing: { EmptyView() },
                                onClicked: {
                                    onOptionSelected(option)
                                }
                            )
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color(.separator).opacity(0.55), lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                        .disabled(!enabled)
                    }
                }

                if !actionOptions.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(actionOptions, id: \.id) { option in
                            Button {
                                if enabled {
                                    onOptionSelected(option)
                                }
                            } label: {
                                HStack {
                                    Text(option.localizedLabel.localizedAgentText)
                                        .frame(maxWidth: .infinity, alignment: .center)
                                }
                                .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.bordered)
                            .frame(maxWidth: .infinity)
                            .disabled(!enabled)
                        }
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

private struct AgentConfirmationRequestView: View {
    let request: AgentInputRequest
    let actionOptions: [AgentInputRequest.Option]
    let confirmOption: AgentInputRequest.Option
    let enabled: Bool
    let onOptionSelected: (AgentInputRequest.Option) -> Void

    private var title: String {
        request.localizedPrompt.localizedAgentText
            .split(separator: "\n", omittingEmptySubsequences: false)
            .first
            .map(String.init) ?? String(localized: "agent_compose_confirmation_prompt", defaultValue: "Send this post?")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title.isEmpty ? String(localized: "agent_compose_confirmation_prompt", defaultValue: "Send this post?") : title)
                .font(.subheadline)
                .foregroundStyle(.primary)
                .fixedSize(horizontal: false, vertical: true)

            if let previewPost = request.postPreview {
                StatusInsightPostPreview(
                    post: previewPost,
                    onClick: {}
                )
            }

            if let previewUser = request.userPreview {
                UserCompatView(
                    data: previewUser,
                    trailing: { EmptyView() },
                    onClicked: {}
                )
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color(.separator).opacity(0.55), lineWidth: 1)
                )
            }

            AgentConfirmationButtonsView(
                actionOptions: actionOptions,
                confirmOption: confirmOption,
                enabled: enabled,
                onOptionSelected: onOptionSelected
            )
        }
    }
}

private struct AgentConfirmationButtonsView: View {
    let actionOptions: [AgentInputRequest.Option]
    let confirmOption: AgentInputRequest.Option
    let enabled: Bool
    let onOptionSelected: (AgentInputRequest.Option) -> Void

    var body: some View {
        HStack(spacing: 8) {
            ForEach(actionOptions, id: \.id) { option in
                if option.id == confirmOption.id {
                    Button {
                        if enabled {
                            onOptionSelected(option)
                        }
                    } label: {
                        Text(option.localizedLabel.localizedAgentText)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
                    .disabled(!enabled)
                } else {
                    Button(role: option.id == "cancel" ? .cancel : nil) {
                        if enabled {
                            onOptionSelected(option)
                        }
                    } label: {
                        Text(option.localizedLabel.localizedAgentText)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
                    .disabled(!enabled)
                }
            }
        }
    }
}

private struct AgentChatMessagesView: UIViewControllerRepresentable {
    let rows: [AgentChatRow]
    let bottomAccessoryInset: CGFloat

    func makeUIViewController(context: Context) -> AgentChatMessagesController {
        let controller = AgentChatMessagesController()
        controller.bottomAccessoryInset = bottomAccessoryInset
        controller.update(rows: rows)
        return controller
    }

    func updateUIViewController(_ controller: AgentChatMessagesController, context: Context) {
        controller.bottomAccessoryInset = bottomAccessoryInset
        controller.update(rows: rows)
    }
}

private struct AgentChatRow {
    let id: String
    let renderHash: Int
    let content: AgentChatRowContent

    static func leading(content: AnyView) -> AgentChatRow {
        AgentChatRow(
            id: "leading-content",
            renderHash: 0,
            content: .leading(content)
        )
    }

    static func message(
        id: String,
        text: String,
        parts: [AgentMessagePart],
        inputRequest: AgentInputRequest?,
        inputRequestSelected: Bool,
        inputRequestSelectedOptionId: String?,
        isUser: Bool,
        onInputRequestOptionSelected: @escaping (AgentInputRequest.Option) -> Void,
        onPostClick: @escaping (UiTimelineV2.Post) -> Void,
        onUserClick: @escaping (UiProfile) -> Void
    ) -> AgentChatRow {
        AgentChatRow(
            id: id,
            renderHash: text.hashValue ^ parts.renderHash ^ isUser.hashValue ^ (inputRequest?.requestId.hashValue ?? 0) ^ inputRequestSelected.hashValue ^ (inputRequestSelectedOptionId?.hashValue ?? 0),
            content: .message(
                text: text,
                parts: parts,
                inputRequest: inputRequest,
                inputRequestSelected: inputRequestSelected,
                inputRequestSelectedOptionId: inputRequestSelectedOptionId,
                isUser: isUser,
                onInputRequestOptionSelected: onInputRequestOptionSelected,
                onPostClick: onPostClick,
                onUserClick: onUserClick
            )
        )
    }

    static func running(text: String) -> AgentChatRow {
        AgentChatRow(
            id: "running",
            renderHash: text.hashValue,
            content: .running(text)
        )
    }

    static func error(text: String) -> AgentChatRow {
        AgentChatRow(
            id: "error",
            renderHash: text.hashValue,
            content: .error(text)
        )
    }
}

private enum AgentChatRowContent {
    case leading(AnyView)
    case message(
        text: String,
        parts: [AgentMessagePart],
        inputRequest: AgentInputRequest?,
        inputRequestSelected: Bool,
        inputRequestSelectedOptionId: String?,
        isUser: Bool,
        onInputRequestOptionSelected: (AgentInputRequest.Option) -> Void,
        onPostClick: (UiTimelineV2.Post) -> Void,
        onUserClick: (UiProfile) -> Void
    )
    case running(String)
    case error(String)
}

@MainActor
private final class AgentChatMessagesController: UIViewController, UICollectionViewDelegate, ChatLayoutDelegate {
    private enum Section {
        static let main = 0
    }
    private let chatLayout = CollectionViewChatLayout()
    private var collectionView: UICollectionView!
    private var dataSource: UICollectionViewDiffableDataSource<Int, String>!
    private var rowsByID: [String: AgentChatRow] = [:]
    private var rowIDs: [String] = []
    private var renderHashes: [String: Int] = [:]
    private var didApplyInitialRows = false
    private var shouldStickToBottom = true
    var bottomAccessoryInset: CGFloat = 0 {
        didSet {
            guard abs(bottomAccessoryInset - oldValue) > 0.5 else { return }
            guard isViewLoaded else { return }

            let shouldScrollToBottom = shouldStickToBottom || isNearBottom
            updateCollectionInsets()
            if shouldScrollToBottom {
                requestScrollToBottom(animated: false)
            }
        }
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        setupCollectionView()
        setupDataSource()
        applySnapshot(animated: false)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        if !didApplyInitialRows, collectionView.numberOfItems(inSection: Section.main) > 0 {
            didApplyInitialRows = true
            requestScrollToBottom(animated: false)
        } else if shouldStickToBottom {
            scrollToBottom(animated: false)
        }
    }

    func update(rows: [AgentChatRow]) {
        rowsByID = Dictionary(uniqueKeysWithValues: rows.map { ($0.id, $0) })
        rowIDs = rows.map(\.id)
        let nextRenderHashes = Dictionary(uniqueKeysWithValues: rows.map { ($0.id, $0.renderHash) })
        let needsReconfigure = renderHashes != nextRenderHashes
        renderHashes = nextRenderHashes

        guard isViewLoaded else { return }
        applySnapshot(animated: didApplyInitialRows)
        if needsReconfigure {
            reconfigureVisibleCells()
            collectionView.collectionViewLayout.invalidateLayout()
        }
    }

    private func setupCollectionView() {
        chatLayout.settings.interItemSpacing = 10
        chatLayout.settings.additionalInsets = UIEdgeInsets(top: 12, left: 0, bottom: 12, right: 0)
        chatLayout.keepContentAtBottomOfVisibleArea = true
        chatLayout.keepContentOffsetAtBottomOnBatchUpdates = true
        chatLayout.processOnlyVisibleItemsOnAnimatedBatchUpdates = false
        chatLayout.delegate = self

        collectionView = UICollectionView(frame: .zero, collectionViewLayout: chatLayout)
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        collectionView.alwaysBounceVertical = true
        collectionView.backgroundColor = .systemGroupedBackground
        collectionView.keyboardDismissMode = .interactive
        collectionView.contentInsetAdjustmentBehavior = .always
        collectionView.automaticallyAdjustsScrollIndicatorInsets = true
        collectionView.delegate = self
        collectionView.register(AgentChatCollectionViewCell.self, forCellWithReuseIdentifier: AgentChatCollectionViewCell.reuseIdentifier)
        updateCollectionInsets()

        view.backgroundColor = .systemGroupedBackground
        view.addSubview(collectionView)
        NSLayoutConstraint.activate([
            collectionView.topAnchor.constraint(equalTo: view.topAnchor),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    private func setupDataSource() {
        dataSource = UICollectionViewDiffableDataSource<Int, String>(
            collectionView: collectionView
        ) { [weak self] collectionView, indexPath, id in
            guard let self,
                  let row = self.rowsByID[id] else {
                return nil
            }

            let cell = collectionView.dequeueReusableCell(
                withReuseIdentifier: AgentChatCollectionViewCell.reuseIdentifier,
                for: indexPath
            ) as! AgentChatCollectionViewCell
            cell.configure(row: row)
            return cell
        }
    }

    private func updateCollectionInsets() {
        guard collectionView != nil else { return }
        let bottomInset = bottomAccessoryInset
        var contentInset = collectionView.contentInset
        contentInset.bottom = bottomInset
        collectionView.contentInset = contentInset

        var scrollIndicatorInsets = collectionView.verticalScrollIndicatorInsets
        scrollIndicatorInsets.bottom = bottomInset
        collectionView.verticalScrollIndicatorInsets = scrollIndicatorInsets
    }

    private func applySnapshot(animated: Bool) {
        guard dataSource != nil else { return }

        let shouldScrollToBottom = shouldStickToBottom || isNearBottom
        let positionSnapshot = shouldScrollToBottom ? nil : chatLayout.getContentOffsetSnapshot(from: .top)
        var snapshot = NSDiffableDataSourceSnapshot<Int, String>()
        snapshot.appendSections([Section.main])
        snapshot.appendItems(rowIDs, toSection: Section.main)
        dataSource.apply(snapshot, animatingDifferences: animated) { [weak self] in
            guard let self else { return }
            if shouldScrollToBottom {
                self.requestScrollToBottom(animated: false)
            } else if let positionSnapshot {
                self.chatLayout.restoreContentOffset(with: positionSnapshot)
            }
        }
    }

    private var isNearBottom: Bool {
        guard collectionView.bounds.height > 0 else { return true }
        return collectionView.contentOffset.y >= bottomContentOffsetY - 80
    }

    private var bottomContentOffsetY: CGFloat {
        max(
            -collectionView.adjustedContentInset.top,
            collectionView.contentSize.height - collectionView.bounds.height + collectionView.adjustedContentInset.bottom
        )
    }

    private func requestScrollToBottom(animated: Bool) {
        shouldStickToBottom = true
        scrollToBottom(animated: animated)
        DispatchQueue.main.async { [weak self] in
            guard let self, self.shouldStickToBottom else { return }
            self.scrollToBottom(animated: false)
        }
    }

    private func scrollToBottom(animated: Bool) {
        guard collectionView.bounds.height > 0 else { return }
        collectionView.layoutIfNeeded()
        collectionView.setContentOffset(
            CGPoint(
                x: collectionView.contentOffset.x,
                y: bottomContentOffsetY
            ),
            animated: animated
        )
    }

    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        guard scrollView === collectionView else { return }
        shouldStickToBottom = false
    }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        guard scrollView === collectionView, !decelerate else { return }
        shouldStickToBottom = isNearBottom
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        guard scrollView === collectionView else { return }
        shouldStickToBottom = isNearBottom
    }

    private func reconfigureVisibleCells() {
        for cell in collectionView.visibleCells {
            guard let indexPath = collectionView.indexPath(for: cell),
                  let id = dataSource.itemIdentifier(for: indexPath),
                  let row = rowsByID[id],
                  let agentCell = cell as? AgentChatCollectionViewCell else {
                continue
            }
            agentCell.configure(row: row)
        }
    }

    func sizeForItem(_ chatLayout: CollectionViewChatLayout, at indexPath: IndexPath) -> ItemSize {
        .estimated(CGSize(width: chatLayout.layoutFrame.width, height: 72))
    }

    func alignmentForItem(_ chatLayout: CollectionViewChatLayout, at indexPath: IndexPath) -> ChatItemAlignment {
        .fullWidth
    }
}

private final class AgentChatCollectionViewCell: UICollectionViewCell {
    static let reuseIdentifier = "AgentChatCollectionViewCell"

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        contentView.backgroundColor = .clear
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        contentConfiguration = nil
    }

    func configure(row: AgentChatRow) {
        contentConfiguration = UIHostingConfiguration {
            AgentChatRowView(row: row)
        }
        .margins(.all, 0)
        .background(.clear)
        setNeedsLayout()
    }

    override func preferredLayoutAttributesFitting(_ layoutAttributes: UICollectionViewLayoutAttributes) -> UICollectionViewLayoutAttributes {
        guard let attributes = layoutAttributes.copy() as? ChatLayoutAttributes else {
            return super.preferredLayoutAttributesFitting(layoutAttributes)
        }

        let width = attributes.layoutFrame.width > 0 ? attributes.layoutFrame.width : layoutAttributes.size.width
        contentView.bounds = CGRect(x: 0, y: 0, width: width, height: contentView.bounds.height)
        contentView.setNeedsLayout()
        contentView.layoutIfNeeded()

        let size = contentView.systemLayoutSizeFitting(
            CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: .fittingSizeLevel
        )
        attributes.size = CGSize(width: width, height: max(ceil(size.height), 1))
        attributes.alignment = .fullWidth
        return attributes
    }
}

private struct AgentChatRowView: View {
    let row: AgentChatRow

    var body: some View {
        Group {
            switch row.content {
            case .leading(let content):
                content
                    .padding(.horizontal)
            case .message(let text, let parts, let inputRequest, let inputRequestSelected, let inputRequestSelectedOptionId, let isUser, let onInputRequestOptionSelected, let onPostClick, let onUserClick):
                AgentChatMessageBubble(
                    text: text,
                    parts: parts,
                    inputRequest: inputRequest,
                    inputRequestSelected: inputRequestSelected,
                    inputRequestSelectedOptionId: inputRequestSelectedOptionId,
                    isUser: isUser,
                    onInputRequestOptionSelected: onInputRequestOptionSelected,
                    onPostClick: onPostClick,
                    onUserClick: onUserClick
                )
            case .running(let text):
                StatusInsightCurrentTrace(trace: text)
                    .padding(.horizontal)
            case .error(let text):
                Text(verbatim: text)
                    .foregroundStyle(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct AgentChatMessageBubble: View {
    let text: String
    let parts: [AgentMessagePart]
    let inputRequest: AgentInputRequest?
    let inputRequestSelected: Bool
    let inputRequestSelectedOptionId: String?
    let isUser: Bool
    let onInputRequestOptionSelected: (AgentInputRequest.Option) -> Void
    let onPostClick: (UiTimelineV2.Post) -> Void
    let onUserClick: (UiProfile) -> Void

    var body: some View {
        HStack {
            if isUser {
                Spacer(minLength: 44)
            }

            messageContent
                    .textSelection(.enabled)
                    .padding(12)
                    .foregroundStyle(isUser ? .white : .primary)
                    .fixedSize(horizontal: false, vertical: true)
                    .background(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(isUser ? Color.accentColor : Color(.systemBackground))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(isUser ? Color.clear : Color(.separator).opacity(0.35), lineWidth: 1)
                    )

            if !isUser {
                Spacer(minLength: 44)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal)
    }

    @ViewBuilder
    private var messageContent: some View {
        VStack(alignment: .leading, spacing: 10) {
            if parts.isEmpty {
                fallbackMessageText
            } else {
                ForEach(Array(parts.enumerated()), id: \.offset) { _, part in
                    switch part {
                    case let textPart as AgentMessagePartText:
                        markdownText(textPart.markdown)
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
                    default:
                        EmptyView()
                    }
                }
            }
            if !isUser, let inputRequest {
                AgentInputRequestOptionsView(
                    request: inputRequest,
                    enabled: !inputRequestSelected,
                    selectedOptionId: inputRequestSelectedOptionId,
                    onOptionSelected: onInputRequestOptionSelected
                )
            }
        }
    }

    private var fallbackMessageText: Text {
        markdownText(text)
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
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color(.separator).opacity(0.55), lineWidth: 1)
        )
    }
}

private extension Array where Element == AgentMessagePart {
    var renderHash: Int {
        reduce(count.hashValue) { partial, part in
            partial ^ String(describing: type(of: part)).hashValue
        }
    }
}

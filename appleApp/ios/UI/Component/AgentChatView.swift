import ChatLayout
import SwiftUI
import SwiftUIBackports
import KotlinSharedUI
import FlareAppleUI

struct AgentChatView: View {
    let messages: [AgentChatHistoryMessage]
    let input: String
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

    @State private var draft: String = ""
    @State private var inputBarHeight: CGFloat = 0

    init(
        messages: [AgentChatHistoryMessage],
        input: String,
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
        self.input = input
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
        ZStack {
            Color(.systemGroupedBackground)
                .ignoresSafeArea()

            AgentChatMessagesView(rows: rows, bottomAccessoryInset: inputBarHeight)
                .ignoresSafeArea(.container, edges: .vertical)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            HStack(alignment: .center, spacing: 10) {
                TextField(inputPlaceholder, text: $draft, axis: .vertical)
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
            contentsOf: messages.map { message in
                .message(
                    id: "message-\(message.id)",
                    parts: Array(message.parts),
                    isUser: message.isUser,
                    onInputRequestOptionSelected: onInputRequestOptionSelected,
                    onPostClick: onPostClick,
                    onUserClick: onUserClick
                )
            }
        )

        if isRunning {
            result.append(.running(text: runningTrace))
        }

        if let errorMessage {
            result.append(.error(text: errorMessage))
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

            AgentRequestPreviewView(request: request)

            if !actionOptions.isEmpty {
                AgentRequestActionButtonsView(
                    actionOptions: actionOptions,
                    enabled: enabled,
                    onOptionSelected: onOptionSelected
                )
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
        }
    }
}

private struct AgentRequestActionButtonsView: View {
    let actionOptions: [AgentInputRequest.Option]
    let enabled: Bool
    let onOptionSelected: (AgentInputRequest.Option) -> Void

    var body: some View {
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

private struct AgentActionOptionButton: View {
    let option: AgentInputRequest.Option
    let enabled: Bool
    let onOptionSelected: (AgentInputRequest.Option) -> Void

    var body: some View {
        if option.buttonType == .primary {
            Button(role: option.buttonRole) {
                if enabled {
                    onOptionSelected(option)
                }
            } label: {
                Text(option.label)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .frame(maxWidth: .infinity)
            .disabled(!enabled)
        } else {
            Button(role: option.buttonRole) {
                if enabled {
                    onOptionSelected(option)
                }
            } label: {
                Text(option.label)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .frame(maxWidth: .infinity)
            .disabled(!enabled)
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
        parts: [AgentMessagePart],
        isUser: Bool,
        onInputRequestOptionSelected: @escaping (AgentInputRequest.Option) -> Void,
        onPostClick: @escaping (UiTimelineV2.Post) -> Void,
        onUserClick: @escaping (UiProfile) -> Void
    ) -> AgentChatRow {
        AgentChatRow(
            id: id,
            renderHash: parts.renderHash ^ isUser.hashValue,
            content: .message(
                parts: parts,
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
        parts: [AgentMessagePart],
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
            case .message(let parts, let isUser, let onInputRequestOptionSelected, let onPostClick, let onUserClick):
                AgentChatMessageBubble(
                    parts: parts,
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
    let parts: [AgentMessagePart]
    let isUser: Bool
    let onInputRequestOptionSelected: (AgentInputRequest.Option) -> Void
    let onPostClick: (UiTimelineV2.Post) -> Void
    let onUserClick: (UiProfile) -> Void

    var body: some View {
        HStack {
            if isUser {
                Spacer(minLength: 44)
            }

            if isPreviewOnlyUserMessage {
                messageContent
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
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
            }

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

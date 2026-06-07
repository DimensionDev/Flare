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
    let inputPlaceholder: String
    let messageText: (Message) -> String
    let isUserMessage: (Message) -> Bool
    let onInputChange: (String) -> Void
    let onSend: () -> Void
    private let leadingContent: () -> AnyView

    @State private var draft: String = ""

    init(
        messages: [Message],
        input: String,
        isRunning: Bool,
        canSend: Bool,
        error: KotlinThrowable?,
        runningTrace: String,
        inputPlaceholder: String,
        messageText: @escaping (Message) -> String,
        isUserMessage: @escaping (Message) -> Bool,
        onInputChange: @escaping (String) -> Void,
        onSend: @escaping () -> Void,
        leadingContent: @escaping () -> AnyView = { AnyView(EmptyView()) }
    ) {
        self.messages = messages
        self.input = input
        self.isRunning = isRunning
        self.canSend = canSend
        self.error = error
        self.runningTrace = runningTrace
        self.inputPlaceholder = inputPlaceholder
        self.messageText = messageText
        self.isUserMessage = isUserMessage
        self.onInputChange = onInputChange
        self.onSend = onSend
        self.leadingContent = leadingContent
    }

    var body: some View {
        AgentChatMessagesView(rows: rows)
            .background(Color(.systemGroupedBackground))
            .ignoresSafeArea()
            .safeAreaInset(edge: .bottom) {
                HStack(alignment: .center, spacing: 10) {
                    TextField(inputPlaceholder, text: $draft, axis: .vertical)
                        .lineLimit(1...4)
                        .disabled(isRunning)
                        .submitLabel(.send)
                        .onSubmit {
                            if canSend {
                                submit()
                            }
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
                .padding([.horizontal, .bottom])
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
                .message(
                    id: "message-\(index)",
                    text: messageText(message),
                    isUser: isUserMessage(message)
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
        onSend()
        draft = ""
    }
}

private struct AgentChatMessagesView: UIViewControllerRepresentable {
    let rows: [AgentChatRow]

    func makeUIViewController(context: Context) -> AgentChatMessagesController {
        let controller = AgentChatMessagesController()
        controller.update(rows: rows)
        return controller
    }

    func updateUIViewController(_ controller: AgentChatMessagesController, context: Context) {
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

    static func message(id: String, text: String, isUser: Bool) -> AgentChatRow {
        AgentChatRow(
            id: id,
            renderHash: text.hashValue ^ isUser.hashValue,
            content: .message(text: text, isUser: isUser)
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
    case message(text: String, isUser: Bool)
    case running(String)
    case error(String)
}

@MainActor
private final class AgentChatMessagesController: UIViewController, UICollectionViewDelegate, ChatLayoutDelegate {
    private enum Section {
        static let main = 0
    }
    private enum Metrics {
        static let bottomInputInset: CGFloat = 112
    }

    private let chatLayout = CollectionViewChatLayout()
    private var collectionView: UICollectionView!
    private var dataSource: UICollectionViewDiffableDataSource<Int, String>!
    private var rowsByID: [String: AgentChatRow] = [:]
    private var rowIDs: [String] = []
    private var renderHashes: [String: Int] = [:]
    private var didApplyInitialRows = false
    private var keyboardBottomInset: CGFloat = 0

    override func viewDidLoad() {
        super.viewDidLoad()
        setupCollectionView()
        setupDataSource()
        setupKeyboardObservers()
        applySnapshot(animated: false)
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        if !didApplyInitialRows, collectionView.numberOfItems(inSection: Section.main) > 0 {
            didApplyInitialRows = true
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

    private func setupKeyboardObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(keyboardWillChangeFrame(_:)),
            name: UIResponder.keyboardWillChangeFrameNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(keyboardWillHide(_:)),
            name: UIResponder.keyboardWillHideNotification,
            object: nil
        )
    }

    @objc private func keyboardWillChangeFrame(_ notification: Notification) {
        guard let endFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else {
            return
        }

        let wasNearBottom = isNearBottom
        let convertedEndFrame = view.convert(endFrame, from: nil)
        keyboardBottomInset = max(0, view.bounds.maxY - convertedEndFrame.minY)
        updateCollectionInsets()
        if wasNearBottom {
            scrollToBottom(animated: false)
        }
    }

    @objc private func keyboardWillHide(_ notification: Notification) {
        let wasNearBottom = isNearBottom
        keyboardBottomInset = 0
        updateCollectionInsets()
        if wasNearBottom {
            scrollToBottom(animated: false)
        }
    }

    private func updateCollectionInsets() {
        guard collectionView != nil else { return }
        let bottomInset = Metrics.bottomInputInset + keyboardBottomInset
        var contentInset = collectionView.contentInset
        contentInset.bottom = bottomInset
        collectionView.contentInset = contentInset

        var scrollIndicatorInsets = collectionView.verticalScrollIndicatorInsets
        scrollIndicatorInsets.bottom = bottomInset
        collectionView.verticalScrollIndicatorInsets = scrollIndicatorInsets
    }

    private func applySnapshot(animated: Bool) {
        guard dataSource != nil else { return }

        let wasNearBottom = isNearBottom
        let positionSnapshot = wasNearBottom ? nil : chatLayout.getContentOffsetSnapshot(from: .top)
        var snapshot = NSDiffableDataSourceSnapshot<Int, String>()
        snapshot.appendSections([Section.main])
        snapshot.appendItems(rowIDs, toSection: Section.main)
        dataSource.apply(snapshot, animatingDifferences: animated) { [weak self] in
            guard let self else { return }
            if wasNearBottom {
                self.scrollToBottom(animated: false)
            } else if let positionSnapshot {
                self.chatLayout.restoreContentOffset(with: positionSnapshot)
            }
        }
    }

    private var isNearBottom: Bool {
        guard collectionView.bounds.height > 0 else { return true }
        let maxOffsetY = max(
            -collectionView.adjustedContentInset.top,
            collectionView.contentSize.height - collectionView.bounds.height + collectionView.adjustedContentInset.bottom
        )
        return collectionView.contentOffset.y >= maxOffsetY - 80
    }

    private func scrollToBottom(animated: Bool) {
        let itemCount = collectionView.numberOfItems(inSection: Section.main)
        guard itemCount > 0 else { return }
        collectionView.scrollToItem(
            at: IndexPath(item: itemCount - 1, section: Section.main),
            at: .bottom,
            animated: animated
        )
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
            case .message(let text, let isUser):
                AgentChatMessageBubble(text: text, isUser: isUser)
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
    let isUser: Bool

    var body: some View {
        HStack {
            if isUser {
                Spacer(minLength: 44)
            }

            messageTextView
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

    private var messageTextView: Text {
        if isUser {
            Text(verbatim: text)
        } else if let attributedText = try? AttributedString(
            markdown: text,
            options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        ) {
            Text(attributedText)
        } else {
            Text(verbatim: text)
        }
    }
}

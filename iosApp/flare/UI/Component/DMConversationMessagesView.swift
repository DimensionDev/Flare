import ChatLayout
import Kingfisher
import SwiftUI
@preconcurrency import KotlinSharedUI

struct DMConversationMessagesView: UIViewControllerRepresentable {
    let data: PagingState<UiDMItem>
    let onRetry: (MicroBlogKey) -> Void
    let onOpenURL: (URL) -> Void

    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.globalAppearance) private var globalAppearance

    func makeUIViewController(context: Context) -> DMConversationMessagesController {
        let controller = DMConversationMessagesController()
        apply(to: controller)
        return controller
    }

    func updateUIViewController(_ controller: DMConversationMessagesController, context: Context) {
        apply(to: controller)
    }

    private func apply(to controller: DMConversationMessagesController) {
        controller.appearance = StatusUIKitAppearance(
            timeline: timelineAppearance,
            fontSizeDiff: globalAppearance.fontSizeDiff
        )
        controller.onRetry = onRetry
        controller.onOpenURL = onOpenURL
        controller.update(data: data)
    }
}

@MainActor
final class DMConversationMessagesController: UIViewController, UICollectionViewDelegate, ChatLayoutDelegate {
    var onRetry: ((MicroBlogKey) -> Void)?
    var onOpenURL: ((URL) -> Void)?
    var appearance = StatusUIKitAppearance(timeline: TimelineAppearance.companion.Default) {
        didSet {
            guard oldValue != appearance, isViewLoaded else { return }
            clearAllHeightCache()
            reconfigureVisibleCells()
            collectionView.collectionViewLayout.invalidateLayout()
            collectionView.performBatchUpdates(nil)
        }
    }

    private enum ItemID {
        static let section = 0
        static let empty = "__dm_empty__"
        static let error = "__dm_error__"
        static let loadingPrefix = "__dm_loading_"
        static let messagePrefix = "__dm_message_"
        static let placeholderPrefix = "__dm_placeholder_"
    }

    private struct SnapshotPlan {
        let ids: [String]
        let renderHashes: [String: Int]
    }

    private let chatLayout = CollectionViewChatLayout()
    private var collectionView: UICollectionView!
    private var dataSource: UICollectionViewDiffableDataSource<Int, String>!
    private var currentData: PagingState<UiDMItem>?
    private var currentSuccess: PagingStateSuccess<UiDMItem>?
    private var itemIndexMap: [String: Int] = [:]
    private var lastAppliedIDs: [String] = []
    private var lastAppliedRenderHashes: [String: Int] = [:]
    private var lastKnownMessageIDByIndex: [Int: String] = [:]
    private var lastKnownMessageRenderHashByItemID: [String: Int] = [:]
    private var heightCache: [String: CGFloat] = [:]
    private var heightCacheKeysByItemID: [String: Set<String>] = [:]
    private var didApplyInitialData = false

    override func viewDidLoad() {
        super.viewDidLoad()
        setupCollectionView()
        setupDataSource()
        view.backgroundColor = .systemGroupedBackground
        if let currentData {
            applySnapshot(data: currentData)
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        if !didApplyInitialData, collectionView.numberOfItems(inSection: ItemID.section) > 0 {
            didApplyInitialData = true
            scrollToBottom(animated: false)
        }
    }

    func update(data: PagingState<UiDMItem>) {
        currentData = data
        if case .success(let success) = onEnum(of: data) {
            currentSuccess = success
        } else {
            currentSuccess = nil
        }
        guard isViewLoaded else { return }
        applySnapshot(data: data)
    }

    private func setupCollectionView() {
        chatLayout.settings.interItemSpacing = 8
        chatLayout.settings.additionalInsets = UIEdgeInsets(top: 8, left: 0, bottom: 8, right: 0)
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
        collectionView.register(DMMessageCollectionViewCell.self, forCellWithReuseIdentifier: DMMessageCollectionViewCell.reuseIdentifier)

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
            guard let self else { return nil }
            let cell = collectionView.dequeueReusableCell(
                withReuseIdentifier: DMMessageCollectionViewCell.reuseIdentifier,
                for: indexPath
            ) as! DMMessageCollectionViewCell
            self.configure(cell, id: id)
            return cell
        }
    }

    private func configure(_ cell: DMMessageCollectionViewCell, id: String) {
        if id.hasPrefix(ItemID.loadingPrefix) || id.hasPrefix(ItemID.placeholderPrefix) {
            cell.configure(
                mode: .placeholder,
                appearance: appearance,
                onRetry: nil,
                onOpenURL: onOpenURL,
                cachedPreferredHeight: nil,
                onPreferredHeightChanged: nil
            )
            return
        }

        if id == ItemID.empty {
            cell.configure(
                mode: .system(text: localized("dm_list_empty", fallback: "No direct messages")),
                appearance: appearance,
                onRetry: nil,
                onOpenURL: onOpenURL,
                cachedPreferredHeight: nil,
                onPreferredHeightChanged: nil
            )
            return
        }

        if id == ItemID.error {
            let message = currentErrorMessage ?? localized("dm_list_error", fallback: "Failed to load direct messages")
            cell.configure(
                mode: .system(text: message),
                appearance: appearance,
                onRetry: nil,
                onOpenURL: onOpenURL,
                cachedPreferredHeight: nil,
                onPreferredHeightChanged: nil
            )
            return
        }

        guard let success = currentSuccess,
              let pagingIndex = itemIndexMap[id] else {
            cell.configure(
                mode: .placeholder,
                appearance: appearance,
                onRetry: nil,
                onOpenURL: onOpenURL,
                cachedPreferredHeight: cachedPreferredHeightProvider(for: id),
                onPreferredHeightChanged: nil
            )
            return
        }

        let item = success.get(index: Int32(pagingIndex))
        let renderHash = item?.hash ?? lastKnownMessageRenderHashByItemID[id]
        cell.configure(
            mode: .message(item),
            appearance: appearance,
            onRetry: { [weak self] key in self?.onRetry?(key) },
            onOpenURL: onOpenURL,
            cachedPreferredHeight: cachedPreferredHeightProvider(for: id),
            onPreferredHeightChanged: item.map { item in
                { [weak self] width, height in
                    self?.applyMeasuredHeight(
                        itemID: id,
                        width: width,
                        height: height
                    )
                }
            }
        )
        if let renderHash {
            lastKnownMessageRenderHashByItemID[id] = renderHash
        }
    }

    private var currentErrorMessage: String? {
        guard let currentData else { return nil }
        if case .error(let error) = onEnum(of: currentData) {
            return error.error.message
        }
        return nil
    }

    private func applySnapshot(data: PagingState<UiDMItem>) {
        let wasNearBottom = isNearBottom
        let previousIDs = lastAppliedIDs
        let previousRenderHashes = lastAppliedRenderHashes
        let plan = makeSnapshotPlan(data: data)
        let ids = plan.ids
        clearChangedHeightCache(previousRenderHashes: previousRenderHashes, newRenderHashes: plan.renderHashes)
        lastAppliedIDs = ids
        lastAppliedRenderHashes = plan.renderHashes

        if previousIDs == ids {
            reconfigureVisibleCells()
            if previousRenderHashes != plan.renderHashes {
                invalidateLayoutPreservingPosition(wasNearBottom: wasNearBottom)
            }
            return
        }

        let positionSnapshot = wasNearBottom ? nil : chatLayout.getContentOffsetSnapshot(from: .top)
        var snapshot = NSDiffableDataSourceSnapshot<Int, String>()
        snapshot.appendSections([ItemID.section])
        snapshot.appendItems(ids, toSection: ItemID.section)

        dataSource.apply(snapshot, animatingDifferences: didApplyInitialData) { [weak self] in
            guard let self else { return }
            if wasNearBottom {
                self.scrollToBottom(animated: false)
            } else if let positionSnapshot {
                self.chatLayout.restoreContentOffset(with: positionSnapshot)
            }
        }
    }

    private func makeSnapshotPlan(data: PagingState<UiDMItem>) -> SnapshotPlan {
        itemIndexMap.removeAll(keepingCapacity: true)
        switch onEnum(of: data) {
        case .loading:
            let ids = (0..<5).map { "\(ItemID.loadingPrefix)\($0)" }
            return SnapshotPlan(ids: ids, renderHashes: [:])
        case .error:
            return SnapshotPlan(ids: [ItemID.error], renderHashes: [:])
        case .empty:
            return SnapshotPlan(ids: [ItemID.empty], renderHashes: [:])
        case .success(let success):
            let itemCount = Int(success.itemCount)
            guard itemCount > 0 else {
                return SnapshotPlan(ids: [ItemID.empty], renderHashes: [:])
            }

            var loadedIDsByIndex: [Int: String] = [:]
            var loadedRenderHashesByItemID: [String: Int] = [:]
            var loadedMessageIDs = Set<String>()

            for pagingIndex in 0..<itemCount {
                guard let item = success.peek(index: Int32(pagingIndex)) else {
                    continue
                }
                let id = messageItemID(for: item)
                loadedIDsByIndex[pagingIndex] = id
                loadedRenderHashesByItemID[id] = item.hash
                loadedMessageIDs.insert(id)
            }

            var ids: [String] = []
            var renderHashes: [String: Int] = [:]
            var usedMessageIDs = Set<String>()
            ids.reserveCapacity(itemCount)

            for displayIndex in 0..<itemCount {
                let pagingIndex = itemCount - 1 - displayIndex
                let id: String
                if let loadedID = loadedIDsByIndex[pagingIndex] {
                    id = loadedID
                    if let renderHash = loadedRenderHashesByItemID[id] {
                        renderHashes[id] = renderHash
                    }
                    lastKnownMessageIDByIndex[pagingIndex] = id
                    usedMessageIDs.insert(id)
                } else if let cachedID = lastKnownMessageIDByIndex[pagingIndex],
                          !loadedMessageIDs.contains(cachedID),
                          !usedMessageIDs.contains(cachedID) {
                    id = cachedID
                    if let renderHash = lastKnownMessageRenderHashByItemID[id] {
                        renderHashes[id] = renderHash
                    }
                    usedMessageIDs.insert(id)
                } else {
                    id = "\(ItemID.placeholderPrefix)\(pagingIndex)"
                }
                ids.append(id)
                itemIndexMap[id] = pagingIndex
            }

            for (itemID, renderHash) in loadedRenderHashesByItemID {
                lastKnownMessageRenderHashByItemID[itemID] = renderHash
            }
            let currentIDs = Set(ids)
            pruneHeightCache(keepingItemIDs: currentIDs)
            pruneStabilizedPagingCache(keepingItemIDs: currentIDs, itemCount: itemCount)
            return SnapshotPlan(ids: ids, renderHashes: renderHashes)
        }
    }

    private func messageItemID(for item: UiDMItem) -> String {
        "\(ItemID.messagePrefix)\(item.id)"
    }

    private func clearAllHeightCache() {
        heightCache.removeAll(keepingCapacity: true)
        heightCacheKeysByItemID.removeAll(keepingCapacity: true)
    }

    private func heightCacheWidthKey(for width: CGFloat) -> Int {
        Int((width * UIScreen.main.scale).rounded(.toNearestOrAwayFromZero))
    }

    private func messageHeightCacheKey(itemID: String, width: CGFloat) -> String {
        "\(itemID):\(heightCacheWidthKey(for: width))"
    }

    private func cachedPreferredHeightProvider(for itemID: String) -> ((CGFloat) -> CGFloat?)? {
        guard itemID.hasPrefix(ItemID.messagePrefix) else { return nil }
        return { [weak self] width in
            guard let self else { return nil }
            return self.heightCache[self.messageHeightCacheKey(itemID: itemID, width: width)]
        }
    }

    private func applyMeasuredHeight(itemID: String, width: CGFloat, height: CGFloat) {
        guard width > 1, width.isFinite, height > 0, height.isFinite else { return }
        let key = messageHeightCacheKey(itemID: itemID, width: width)
        let correctedHeight = max(ceil(height), 1)
        if let cachedHeight = heightCache[key],
           abs(cachedHeight - correctedHeight) <= 1 {
            return
        }
        heightCache[key] = correctedHeight
        heightCacheKeysByItemID[itemID, default: []].insert(key)
    }

    private func clearChangedHeightCache(previousRenderHashes: [String: Int], newRenderHashes: [String: Int]) {
        for (itemID, newRenderHash) in newRenderHashes {
            guard let previousRenderHash = previousRenderHashes[itemID],
                  previousRenderHash != newRenderHash else {
                continue
            }
            removeHeightCache(for: itemID)
        }
    }

    private func removeHeightCache(for itemID: String) {
        guard let keys = heightCacheKeysByItemID.removeValue(forKey: itemID) else { return }
        for key in keys {
            heightCache.removeValue(forKey: key)
        }
    }

    private func pruneHeightCache(keepingItemIDs: Set<String>) {
        let existing = Set(heightCacheKeysByItemID.keys)
        let removed = existing.subtracting(keepingItemIDs)
        guard !removed.isEmpty else { return }
        for itemID in removed {
            guard let keys = heightCacheKeysByItemID.removeValue(forKey: itemID) else { continue }
            for key in keys {
                heightCache.removeValue(forKey: key)
            }
        }
    }

    private func pruneStabilizedPagingCache(keepingItemIDs: Set<String>, itemCount: Int) {
        lastKnownMessageIDByIndex = lastKnownMessageIDByIndex.filter { index, itemID in
            index >= 0 && index < itemCount && keepingItemIDs.contains(itemID)
        }
        lastKnownMessageRenderHashByItemID = lastKnownMessageRenderHashByItemID.filter { itemID, _ in
            keepingItemIDs.contains(itemID)
        }
    }

    private var isNearBottom: Bool {
        guard collectionView.bounds.height > 0 else { return true }
        let maxOffsetY = max(
            -collectionView.adjustedContentInset.top,
            collectionView.contentSize.height - collectionView.bounds.height + collectionView.adjustedContentInset.bottom
        )
        return collectionView.contentOffset.y >= maxOffsetY - 60
    }

    private func scrollToBottom(animated: Bool) {
        let itemCount = collectionView.numberOfItems(inSection: ItemID.section)
        guard itemCount > 0 else { return }
        let indexPath = IndexPath(item: itemCount - 1, section: ItemID.section)
        collectionView.scrollToItem(at: indexPath, at: .bottom, animated: animated)
    }

    private func reconfigureVisibleCells() {
        for cell in collectionView.visibleCells {
            guard let indexPath = collectionView.indexPath(for: cell),
                  let id = dataSource.itemIdentifier(for: indexPath),
                  let dmCell = cell as? DMMessageCollectionViewCell else {
                continue
            }
            configure(dmCell, id: id)
        }
    }

    func sizeForItem(_ chatLayout: CollectionViewChatLayout, at indexPath: IndexPath) -> ItemSize {
        let width = chatLayout.layoutFrame.width
        guard let id = dataSource.itemIdentifier(for: indexPath) else {
            return .estimated(CGSize(width: width, height: 64))
        }
        if let height = cachedPreferredHeightProvider(for: id)?(width),
           height > 0,
           height.isFinite {
            return .estimated(CGSize(width: width, height: height))
        }
        switch id {
        case ItemID.empty, ItemID.error:
            return .estimated(CGSize(width: width, height: 96))
        default:
            return .estimated(CGSize(width: width, height: 64))
        }
    }

    func alignmentForItem(_ chatLayout: CollectionViewChatLayout, at indexPath: IndexPath) -> ChatItemAlignment {
        .fullWidth
    }

    private func invalidateLayoutPreservingPosition(wasNearBottom: Bool) {
        let positionSnapshot = wasNearBottom ? nil : chatLayout.getContentOffsetSnapshot(from: .top)
        collectionView.collectionViewLayout.invalidateLayout()
        collectionView.performBatchUpdates(nil) { [weak self] _ in
            guard let self else { return }
            if wasNearBottom {
                self.scrollToBottom(animated: false)
            } else if let positionSnapshot {
                self.chatLayout.restoreContentOffset(with: positionSnapshot)
            }
        }
    }
}

private final class DMMessageCollectionViewCell: UICollectionViewCell {
    static let reuseIdentifier = "DMMessageCollectionViewCell"

    var cachedPreferredHeight: ((CGFloat) -> CGFloat?)?
    var onPreferredHeightChanged: ((CGFloat, CGFloat) -> Void)?

    private let messageView = DMMessageUIView()
    private var lastPreferredHeightReport: (widthKey: Int, height: CGFloat)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        contentView.addSubview(messageView)
        contentView.backgroundColor = .clear
        backgroundColor = .clear
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        messageView.prepareForReuse()
        cachedPreferredHeight = nil
        onPreferredHeightChanged = nil
        lastPreferredHeightReport = nil
    }

    func configure(
        mode: DMMessageUIView.Mode,
        appearance: StatusUIKitAppearance,
        onRetry: ((MicroBlogKey) -> Void)?,
        onOpenURL: ((URL) -> Void)?,
        cachedPreferredHeight: ((CGFloat) -> CGFloat?)?,
        onPreferredHeightChanged: ((CGFloat, CGFloat) -> Void)?
    ) {
        self.cachedPreferredHeight = cachedPreferredHeight
        self.onPreferredHeightChanged = onPreferredHeightChanged
        messageView.configure(mode: mode, appearance: appearance, onRetry: onRetry, onOpenURL: onOpenURL)
        lastPreferredHeightReport = nil
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        messageView.frame = contentView.bounds
        reportPreferredHeightIfNeeded()
    }

    override func preferredLayoutAttributesFitting(_ layoutAttributes: UICollectionViewLayoutAttributes) -> UICollectionViewLayoutAttributes {
        guard let attributes = layoutAttributes.copy() as? ChatLayoutAttributes else {
            return super.preferredLayoutAttributesFitting(layoutAttributes)
        }
        let width = attributes.layoutFrame.width > 0 ? attributes.layoutFrame.width : layoutAttributes.size.width
        let height: CGFloat
        if let cachedHeight = cachedPreferredHeight?(width),
           cachedHeight > 0,
           cachedHeight.isFinite {
            height = cachedHeight
        } else {
            height = measuredHeight(width: width)
            reportPreferredHeight(width: width, height: height)
        }
        attributes.size = CGSize(width: width, height: max(ceil(height), 1))
        attributes.alignment = .fullWidth
        return attributes
    }

    private func measuredHeight(width: CGFloat) -> CGFloat {
        guard width > 1, width.isFinite else { return 1 }
        contentView.bounds = CGRect(x: 0, y: 0, width: width, height: contentView.bounds.height)
        messageView.bounds = CGRect(x: 0, y: 0, width: width, height: messageView.bounds.height)
        messageView.setNeedsLayout()
        return max(ceil(messageView.timelineHeight(for: width) ?? 1), 1)
    }

    private func reportPreferredHeightIfNeeded() {
        guard onPreferredHeightChanged != nil,
              contentView.bounds.width > 1 else {
            return
        }
        let height = measuredHeight(width: contentView.bounds.width)
        reportPreferredHeight(width: contentView.bounds.width, height: height)
    }

    private func reportPreferredHeight(width: CGFloat, height: CGFloat) {
        guard let onPreferredHeightChanged,
              width > 1,
              width.isFinite,
              height > 0,
              height.isFinite else {
            return
        }
        let widthKey = Int((width * UIScreen.main.scale).rounded(.toNearestOrAwayFromZero))
        if let lastPreferredHeightReport,
           lastPreferredHeightReport.widthKey == widthKey,
           abs(lastPreferredHeightReport.height - height) < 0.5 {
            return
        }
        lastPreferredHeightReport = (widthKey, height)
        onPreferredHeightChanged(width, height)
    }
}

private final class DMMessageUIView: UIView, TimelineHeightProviding {
    enum Mode {
        case placeholder
        case system(text: String)
        case message(UiDMItem?)
    }

    private static let horizontalPadding: CGFloat = 16
    private static let avatarSize: CGFloat = 40
    private static let retryButtonSize: CGFloat = 32
    private static let rowSpacing: CGFloat = 6
    private static let metadataSpacing: CGFloat = 4
    private static let maxBubbleWidthFraction: CGFloat = 0.75

    private let avatarView = AvatarUIView()
    private let retryButton = UIButton(type: .system)
    private let bubbleView = DMBubbleView()
    private let senderNameView = RichTextUIView()
    private let dateLabel = DateTimeUILabel()
    private let statusLabel = UILabel()
    private let systemLabel = UILabel()
    private let placeholderView = DMPlaceholderBubbleView()

    private var mode: Mode = .placeholder
    private var appearance = StatusUIKitAppearance(timeline: TimelineAppearance.companion.Default)
    private var onRetry: ((MicroBlogKey) -> Void)?
    private var onOpenURL: ((URL) -> Void)?
    private var messageKeyForRetry: MicroBlogKey?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        setupSubviews()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    private func setupSubviews() {
        avatarView.isHidden = true
        retryButton.isHidden = true
        retryButton.tintColor = .systemRed
        retryButton.setImage(UIImage(named: "fa-circle-exclamation"), for: .normal)
        retryButton.addTarget(self, action: #selector(retryTapped), for: .touchUpInside)

        senderNameView.lineLimit = 1
        senderNameView.baseTextStyle = .caption1
        senderNameView.baseTextColor = .secondaryLabel
        senderNameView.fixedVertical = true
        senderNameView.isHidden = true

        dateLabel.isHidden = true

        statusLabel.font = .preferredFont(forTextStyle: .caption1)
        statusLabel.adjustsFontForContentSizeCategory = true
        statusLabel.textColor = .secondaryLabel
        statusLabel.numberOfLines = 1
        statusLabel.isHidden = true

        systemLabel.font = .preferredFont(forTextStyle: .body)
        systemLabel.adjustsFontForContentSizeCategory = true
        systemLabel.textColor = .secondaryLabel
        systemLabel.textAlignment = .center
        systemLabel.numberOfLines = 0
        systemLabel.isHidden = true

        placeholderView.isHidden = true

        addSubview(avatarView)
        addSubview(retryButton)
        addSubview(bubbleView)
        addSubview(senderNameView)
        addSubview(dateLabel)
        addSubview(statusLabel)
        addSubview(systemLabel)
        addSubview(placeholderView)
    }

    func prepareForReuse() {
        bubbleView.prepareForReuse()
        messageKeyForRetry = nil
        onRetry = nil
        onOpenURL = nil
    }

    func configure(
        mode: Mode,
        appearance: StatusUIKitAppearance,
        onRetry: ((MicroBlogKey) -> Void)?,
        onOpenURL: ((URL) -> Void)?
    ) {
        self.mode = mode
        self.appearance = appearance
        self.onRetry = onRetry
        self.onOpenURL = onOpenURL
        dateLabel.absoluteTimestamp = appearance.absoluteTimestamp
        avatarView.avatarShape = appearance.avatarShape

        switch mode {
        case .placeholder:
            configurePlaceholder()
        case .system(let text):
            configureSystem(text: text)
        case .message(let item):
            guard let item else {
                configurePlaceholder()
                return
            }
            configureMessage(item)
        }
        setNeedsLayout()
        invalidateIntrinsicContentSize()
    }

    private func configurePlaceholder() {
        avatarView.isHidden = true
        retryButton.isHidden = true
        bubbleView.isHidden = true
        senderNameView.isHidden = true
        dateLabel.isHidden = true
        statusLabel.isHidden = true
        systemLabel.isHidden = true
        placeholderView.isHidden = false
    }

    private func configureSystem(text: String) {
        avatarView.isHidden = true
        retryButton.isHidden = true
        bubbleView.isHidden = true
        senderNameView.isHidden = true
        dateLabel.isHidden = true
        statusLabel.isHidden = true
        placeholderView.isHidden = true
        systemLabel.isHidden = false
        systemLabel.text = text
    }

    private func configureMessage(_ item: UiDMItem) {
        placeholderView.isHidden = true
        systemLabel.isHidden = true
        bubbleView.isHidden = false
        avatarView.isHidden = !(item.showSender && !item.isFromMe)
        retryButton.isHidden = item.sendState != .failed
        senderNameView.isHidden = !(item.showSender && !item.isFromMe)
        dateLabel.isHidden = item.sendState == .failed || item.sendState == .sending
        statusLabel.isHidden = item.sendState != .sending

        if !avatarView.isHidden {
            avatarView.set(url: item.user.avatar)
        }
        if !senderNameView.isHidden {
            senderNameView.configure(
                text: item.user.name,
                lineLimit: 1,
                isTextSelectionEnabled: false,
                onOpenURL: onOpenURL,
                baseTextStyle: .caption1,
                baseTextColor: .secondaryLabel,
                preferredContentSizeCategory: appearance.preferredContentSizeCategory
            )
        }
        if !dateLabel.isHidden {
            dateLabel.set(data: item.timestamp)
        }
        if !statusLabel.isHidden {
            statusLabel.text = localized("dm_sending", fallback: "Sending")
        }

        messageKeyForRetry = item.key
        bubbleView.configure(
            item: item,
            appearance: appearance,
            onOpenURL: onOpenURL
        )
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        _ = layout(width: bounds.width, assignFrames: true)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return layout(width: width, assignFrames: false).height
    }

    private func layout(width: CGFloat, assignFrames: Bool) -> CGSize {
        switch mode {
        case .placeholder:
            let targetWidth = max(width * Self.maxBubbleWidthFraction, 120)
            let size = placeholderView.sizeThatFits(CGSize(width: targetWidth, height: .greatestFiniteMagnitude))
            if assignFrames {
                placeholderView.frame = CGRect(
                    x: Self.horizontalPadding,
                    y: 8,
                    width: min(size.width, targetWidth),
                    height: size.height
                )
            }
            return CGSize(width: width, height: size.height + 16)

        case .system:
            let labelWidth = max(width - Self.horizontalPadding * 2, 1)
            let size = systemLabel.sizeThatFits(CGSize(width: labelWidth, height: .greatestFiniteMagnitude))
            if assignFrames {
                systemLabel.frame = CGRect(
                    x: Self.horizontalPadding,
                    y: 16,
                    width: labelWidth,
                    height: ceil(size.height)
                )
            }
            return CGSize(width: width, height: ceil(size.height) + 32)

        case .message(let item):
            guard let item else {
                return CGSize(width: width, height: 56)
            }
            let availableWidth = max(width - Self.horizontalPadding * 2, 1)
            let maxBubbleWidth = floor(availableWidth * Self.maxBubbleWidthFraction)
            let bubbleSize = bubbleView.sizeThatFits(
                CGSize(width: maxBubbleWidth, height: .greatestFiniteMagnitude)
            )

            let hasAvatar = item.showSender && !item.isFromMe
            let hasRetry = item.sendState == .failed
            let avatarWidth = hasAvatar ? Self.avatarSize + Self.rowSpacing : 0
            let retryWidth = hasRetry ? Self.retryButtonSize + Self.rowSpacing : 0
            let rowWidth = min(avatarWidth + retryWidth + bubbleSize.width, availableWidth)
            let rowX = item.isFromMe ? width - Self.horizontalPadding - rowWidth : Self.horizontalPadding
            var x = rowX
            let rowY: CGFloat = 4

            if assignFrames {
                if hasAvatar {
                    avatarView.frame = CGRect(x: x, y: rowY + max(bubbleSize.height - Self.avatarSize, 0), width: Self.avatarSize, height: Self.avatarSize)
                    x += Self.avatarSize + Self.rowSpacing
                } else {
                    avatarView.frame = .zero
                }

                if hasRetry {
                    retryButton.frame = CGRect(x: x, y: rowY + max((bubbleSize.height - Self.retryButtonSize) / 2, 0), width: Self.retryButtonSize, height: Self.retryButtonSize)
                    x += Self.retryButtonSize + Self.rowSpacing
                } else {
                    retryButton.frame = .zero
                }

                bubbleView.frame = CGRect(x: x, y: rowY, width: bubbleSize.width, height: bubbleSize.height)
            }

            let metadataWidth = max(min(bubbleSize.width, maxBubbleWidth), 1)
            var metadataHeight: CGFloat = 0
            let metadataX = item.isFromMe
                ? width - Self.horizontalPadding - metadataWidth
                : rowX + avatarWidth
            let metadataY = rowY + bubbleSize.height + Self.metadataSpacing

            if item.showSender && !item.isFromMe {
                let nameHeight = senderNameView.timelineHeight(for: metadataWidth) ?? 0
                if assignFrames {
                    senderNameView.frame = CGRect(x: metadataX, y: metadataY, width: metadataWidth, height: nameHeight)
                }
                metadataHeight = max(metadataHeight, nameHeight)
            } else if assignFrames {
                senderNameView.frame = .zero
            }

            if item.sendState == .sending {
                let size = statusLabel.sizeThatFits(CGSize(width: metadataWidth, height: .greatestFiniteMagnitude))
                if assignFrames {
                    statusLabel.frame = CGRect(
                        x: metadataX,
                        y: metadataY + metadataHeight,
                        width: metadataWidth,
                        height: ceil(size.height)
                    )
                }
                metadataHeight += ceil(size.height)
            } else if item.sendState != .failed {
                let size = dateLabel.sizeThatFits(CGSize(width: metadataWidth, height: .greatestFiniteMagnitude))
                if assignFrames {
                    dateLabel.frame = CGRect(
                        x: metadataX,
                        y: metadataY + metadataHeight,
                        width: min(size.width, metadataWidth),
                        height: ceil(size.height)
                    )
                }
                metadataHeight += ceil(size.height)
            } else if assignFrames {
                dateLabel.frame = .zero
                statusLabel.frame = .zero
            }

            return CGSize(width: width, height: rowY + bubbleSize.height + (metadataHeight > 0 ? Self.metadataSpacing + metadataHeight : 0) + 4)
        }
    }

    @objc private func retryTapped() {
        guard let messageKeyForRetry else { return }
        onRetry?(messageKeyForRetry)
    }
}

private final class DMBubbleView: UIView {
    private enum Content {
        case none
        case text(RichTextUIView)
        case label(UILabel)
        case media(DMMediaPreviewView)
        case status(StatusUIKitView)
    }

    private var content: Content = .none
    private var isOutgoing = false
    private var usesBubbleBackground = true

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        layer.cornerRadius = 18
        layer.cornerCurve = .continuous
        clipsToBounds = true
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func prepareForReuse() {
        removeCurrentContent()
    }

    func configure(
        item: UiDMItem,
        appearance: StatusUIKitAppearance,
        onOpenURL: ((URL) -> Void)?
    ) {
        removeCurrentContent()
        isOutgoing = item.isFromMe

        switch onEnum(of: item.content) {
        case .text(let message):
            usesBubbleBackground = true
            let textView = RichTextUIView()
            textView.configure(
                text: message.text,
                lineLimit: nil,
                isTextSelectionEnabled: false,
                onOpenURL: onOpenURL,
                baseTextStyle: .body,
                baseTextColor: item.isFromMe ? .white : .label,
                preferredContentSizeCategory: appearance.preferredContentSizeCategory,
                contentKey: item.hash
            )
            content = .text(textView)
            addSubview(textView)

        case .deleted:
            usesBubbleBackground = false
            let label = UILabel()
            label.font = .preferredFont(forTextStyle: .caption1)
            label.adjustsFontForContentSizeCategory = true
            label.textColor = .secondaryLabel
            label.text = localized("dm_deleted", fallback: "Deleted")
            content = .label(label)
            addSubview(label)

        case .media(let message):
            usesBubbleBackground = false
            let mediaView = DMMediaPreviewView()
            mediaView.configure(media: message.media, onOpenURL: onOpenURL)
            content = .media(mediaView)
            addSubview(mediaView)

        case .status(let message):
            usesBubbleBackground = true
            let statusView = StatusUIKitView()
            statusView.openURL = onOpenURL
            statusView.configure(
                data: message.status,
                appearance: appearance,
                isDetail: false,
                isQuote: false,
                withLeadingPadding: false,
                showMedia: appearance.showMedia,
                maxLine: 5,
                showExpandTextButton: true,
                forceHideActions: false,
                showTranslate: true,
                aiTldrEnabled: false,
                showParents: true
            )
            content = .status(statusView)
            addSubview(statusView)
        }

        updateBackground()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let insets = contentInsets
        let contentFrame = bounds.inset(by: insets)
        switch content {
        case .none:
            break
        case .text(let view):
            view.prepareForFitting(width: contentFrame.width)
            view.frame = contentFrame
        case .label(let label):
            label.frame = contentFrame
        case .media(let media):
            media.frame = bounds
        case .status(let status):
            status.frame = contentFrame
        }
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        let maxWidth = max(size.width, 1)
        let insets = contentInsets
        let contentWidth = max(maxWidth - insets.left - insets.right, 1)
        let contentSize: CGSize
        switch content {
        case .none:
            contentSize = .zero
        case .text(let view):
            contentSize = CGSize(width: contentWidth, height: view.timelineHeight(for: contentWidth) ?? 0)
        case .label(let label):
            contentSize = label.sizeThatFits(CGSize(width: contentWidth, height: .greatestFiniteMagnitude))
        case .media(let media):
            contentSize = media.sizeThatFits(CGSize(width: maxWidth, height: .greatestFiniteMagnitude))
            return CGSize(width: contentSize.width, height: contentSize.height)
        case .status(let status):
            contentSize = CGSize(width: contentWidth, height: status.timelineHeight(for: contentWidth) ?? 0)
        }

        let width = min(maxWidth, ceil(contentSize.width + insets.left + insets.right))
        let height = ceil(contentSize.height + insets.top + insets.bottom)
        return CGSize(width: max(width, 44), height: max(height, 24))
    }

    private var contentInsets: UIEdgeInsets {
        usesBubbleBackground ? UIEdgeInsets(top: 8, left: 14, bottom: 8, right: 14) : .zero
    }

    private func updateBackground() {
        if usesBubbleBackground {
            backgroundColor = isOutgoing ? .systemBlue : .secondarySystemGroupedBackground
            layer.cornerRadius = 18
        } else {
            backgroundColor = .clear
            layer.cornerRadius = 12
        }
    }

    private func removeCurrentContent() {
        switch content {
        case .none:
            break
        case .text(let view):
            view.removeFromSuperview()
        case .label(let label):
            label.removeFromSuperview()
        case .media(let media):
            media.removeFromSuperview()
        case .status(let status):
            status.removeFromSuperview()
        }
        content = .none
    }
}

private final class DMMediaPreviewView: UIView {
    private let imageView = UIImageView()
    private let playIconView = UIImageView(image: UIImage(named: "fa-circle-play"))
    private var aspectRatio: CGFloat = 1
    private var onOpenURL: ((URL) -> Void)?
    private var imageRouteURL: URL?

    override init(frame: CGRect) {
        super.init(frame: frame)
        clipsToBounds = true
        layer.cornerRadius = 12
        layer.cornerCurve = .continuous
        backgroundColor = .secondarySystemGroupedBackground
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        playIconView.tintColor = .white
        playIconView.contentMode = .scaleAspectFit
        playIconView.isHidden = true
        addSubview(imageView)
        addSubview(playIconView)
        let tap = UITapGestureRecognizer(target: self, action: #selector(tapped))
        addGestureRecognizer(tap)
        isUserInteractionEnabled = true
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(media: UiMedia, onOpenURL: ((URL) -> Void)?) {
        self.onOpenURL = onOpenURL
        imageRouteURL = nil
        imageView.kf.cancelDownloadTask()
        imageView.image = nil
        playIconView.isHidden = true

        let imageURL: String
        switch onEnum(of: media) {
        case .image(let image):
            imageURL = image.previewUrl
            aspectRatio = boundedAspectRatio(CGFloat(image.aspectRatio))
            imageRouteURL = URL(string: DeeplinkRoute.Media.MediaImage(uri: image.url, previewUrl: image.previewUrl).toUri())
        case .gif(let gif):
            imageURL = gif.url
            aspectRatio = boundedAspectRatio(CGFloat(gif.aspectRatio))
            playIconView.isHidden = false
            imageRouteURL = URL(string: DeeplinkRoute.OpenLinkDirectly(url: gif.url).toUri())
        case .video(let video):
            imageURL = video.thumbnailUrl
            aspectRatio = boundedAspectRatio(CGFloat(video.aspectRatio))
            playIconView.isHidden = false
            imageRouteURL = URL(string: DeeplinkRoute.OpenLinkDirectly(url: video.url).toUri())
        case .audio(let audio):
            imageURL = audio.previewUrl ?? ""
            aspectRatio = 1.6
            imageRouteURL = URL(string: DeeplinkRoute.OpenLinkDirectly(url: audio.url).toUri())
        }

        if let url = URL(string: imageURL) {
            imageView.kf.setImage(with: url, options: [.transition(.fade(0.2)), .cacheOriginalImage, .backgroundDecode])
        }
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        imageView.frame = bounds
        let playSize: CGFloat = 42
        playIconView.frame = CGRect(
            x: (bounds.width - playSize) / 2,
            y: (bounds.height - playSize) / 2,
            width: playSize,
            height: playSize
        )
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        let width = min(max(size.width, 160), 360)
        let height = min(max(width / aspectRatio, 120), 320)
        return CGSize(width: width, height: height)
    }

    private func boundedAspectRatio(_ rawValue: CGFloat) -> CGFloat {
        guard rawValue.isFinite, rawValue > 0 else { return 1 }
        return min(max(rawValue, 0.75), 2.2)
    }

    @objc private func tapped() {
        guard let imageRouteURL else { return }
        onOpenURL?(imageRouteURL)
    }
}

private final class DMPlaceholderBubbleView: UIView {
    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .secondarySystemGroupedBackground
        layer.cornerRadius = 18
        layer.cornerCurve = .continuous
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: min(max(size.width * 0.72, 180), 320), height: 44)
    }
}

private func localized(_ key: String, fallback: String) -> String {
    NSLocalizedString(key, tableName: nil, bundle: .main, value: fallback, comment: "")
}

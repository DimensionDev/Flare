import SwiftUI
import KotlinSharedUI

// MARK: - SwiftUI Wrapper

struct CollectionViewTimelineView: UIViewControllerRepresentable {
    let data: PagingState<UiTimelineV2>
    let detailStatusKey: MicroBlogKey?
    let topContentInset: CGFloat
    @Environment(\.appearanceSettings.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.refresh) private var refreshAction: RefreshAction?

    init(
        data: PagingState<UiTimelineV2>,
        detailStatusKey: MicroBlogKey?,
        topContentInset: CGFloat = 0
    ) {
        self.data = data
        self.detailStatusKey = detailStatusKey
        self.topContentInset = topContentInset
    }

    func makeUIViewController(context: Context) -> CollectionViewTimelineController {
        let controller = CollectionViewTimelineController(detailStatusKey: detailStatusKey)
        controller.refreshCallback = refreshAction.map { action in
            { await action() }
        }
        controller.topContentInset = topContentInset
        controller.usesCardBackground = timelineDisplayMode != .plain
        controller.update(data: data)
        return controller
    }

    func updateUIViewController(_ controller: CollectionViewTimelineController, context: Context) {
        controller.refreshCallback = refreshAction.map { action in
            { await action() }
        }
        controller.topContentInset = topContentInset
        controller.usesCardBackground = timelineDisplayMode != .plain
        controller.update(data: data)
    }
}

// MARK: - Controller

final class CollectionViewTimelineController: UIViewController, UICollectionViewDelegate, UIScrollViewDelegate {

    // Use Int for section and String for item to avoid Sendable issues
    private static let sectionMain = 0
    private static let sectionFooter = 1

    private let detailStatusKey: MicroBlogKey?
    private var currentData: PagingState<UiTimelineV2>?
    private var currentSuccess: PagingStateSuccess<UiTimelineV2>?

    var refreshCallback: (() async -> Void)?
    var topContentInset: CGFloat = 0 {
        didSet {
            guard isViewLoaded else { return }
            updateContentInsets()
        }
    }
    var usesCardBackground: Bool = true {
        didSet {
            guard isViewLoaded else { return }
            updateBackgroundColors()
        }
    }

    private var collectionView: UICollectionView!
    private var dataSource: UICollectionViewDiffableDataSource<Int, String>!
    private var refreshControl = UIRefreshControl()
    private var isUserRefreshing = false
    private var shouldRevealRefreshControl = false
    private var scrollingState = IsScrollingState()
    private var lastAppliedSignature: SnapshotSignature?
    private var lastRenderHashMap: [String: Int32] = [:]

    // Maps item identifier → index for timeline cells
    private var itemIndexMap: [String: Int] = [:]

    private struct SnapshotSignature: Equatable {
        let itemIDs: [String]
        let footerIDs: [String]
    }

    // Item ID prefixes / constants
    private static let timelinePrefix = "t:"
    private static let placeholderPrefix = "p:"
    private static let emptyID = "__empty__"
    private static let errorID = "__error__"
    private static let footerLoadingID = "__fl__"
    private static let footerErrorID = "__fe__"
    private static let footerEndID = "__fend__"

    init(detailStatusKey: MicroBlogKey?) {
        self.detailStatusKey = detailStatusKey
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        setupCollectionView()
        setupDataSource()
        setupRefreshControl()
        updateContentInsets()
        updateBackgroundColors()
        if let data = currentData {
            syncRefreshControl(data: data)
            applySnapshot(data: data)
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        if shouldRevealRefreshControl {
            revealRefreshControlIfNeeded()
        }
    }

    // MARK: - Setup

    private func setupCollectionView() {
        let layout = UICollectionViewCompositionalLayout { _, layoutEnvironment in
            var listConfig = UICollectionLayoutListConfiguration(appearance: .plain)
            listConfig.showsSeparators = false
            listConfig.backgroundColor = .clear
            let section = NSCollectionLayoutSection.list(using: listConfig, layoutEnvironment: layoutEnvironment)
            section.interGroupSpacing = 2
//            section.contentInsets = .init(top: 0, leading: 16, bottom: 0, trailing: 16)
            return section
        }
        collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.delegate = self
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(collectionView)
        NSLayoutConstraint.activate([
            collectionView.topAnchor.constraint(equalTo: view.topAnchor),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    private func setupDataSource() {
        let cellReg = UICollectionView.CellRegistration<UICollectionViewCell, String> {
            [weak self] cell, _, itemID in
            guard let self else { return }
            self.configureCell(cell, itemID: itemID)
        }

        dataSource = UICollectionViewDiffableDataSource<Int, String>(
            collectionView: collectionView
        ) { (collectionView: UICollectionView, indexPath: IndexPath, itemID: String) -> UICollectionViewCell? in
            collectionView.dequeueConfiguredReusableCell(using: cellReg, for: indexPath, item: itemID)
        }
    }

    private func setupRefreshControl() {
        refreshControl.addTarget(self, action: #selector(handleRefresh), for: .valueChanged)
        collectionView.refreshControl = refreshControl
    }

    private func updateContentInsets() {
        guard collectionView != nil else { return }
        collectionView.contentInset.top = topContentInset
        collectionView.verticalScrollIndicatorInsets.top = topContentInset
    }

    private func updateBackgroundColors() {
        let backgroundColor: UIColor = usesCardBackground ? .systemGroupedBackground : .clear
        view.backgroundColor = backgroundColor
        collectionView.backgroundColor = backgroundColor
    }

    // MARK: - Cell Configuration

    private func configureCell(_ cell: UICollectionViewCell, itemID: String) {
        if itemID.hasPrefix(Self.timelinePrefix) {
            if let index = itemIndexMap[itemID] {
                configureTimelineCell(cell, index: index)
            }
        } else if itemID.hasPrefix(Self.placeholderPrefix) {
            let indexStr = itemID.dropFirst(Self.placeholderPrefix.count)
            let index = Int(indexStr) ?? 0
            configurePlaceholderCell(cell, index: index)
        } else if itemID == Self.emptyID {
            configureHostingCell(cell) { ListEmptyView() }
        } else if itemID == Self.errorID {
            configureErrorCell(cell)
        } else if itemID == Self.footerLoadingID {
            configureHostingCell(cell) {
                ProgressView()
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .center)
            }
        } else if itemID == Self.footerErrorID {
            configureFooterErrorCell(cell)
        } else if itemID == Self.footerEndID {
            configureHostingCell(cell) {
                Text("end_of_list")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .center)
            }
        }
    }

    private func configureTimelineCell(_ cell: UICollectionViewCell, index: Int) {
        guard let success = currentSuccess else { return }
        let item = success.peek(index: Int32(index))
        let totalCount = Int(success.itemCount)
        let detailKey = detailStatusKey
        let scrollState = scrollingState
        if let item {
            cell.contentConfiguration = UIHostingConfiguration {
                AdaptiveTimelineCard(index: index, totalCount: totalCount) {
                    TimelineView(data: item, detailStatusKey: detailKey)
                        .padding(.vertical, 12)
                        .padding(.horizontal)
                }
                .environment(\.isScrollingState, scrollState)
            }
            .margins(.all, 0)
            .minSize(width: 0, height: 0)
        } else {
            configurePlaceholderCell(cell, index: index)
        }
    }

    private func configurePlaceholderCell(_ cell: UICollectionViewCell, index: Int) {
        let totalCount: Int
        if let success = currentSuccess {
            totalCount = Int(success.itemCount)
        } else {
            totalCount = 5
        }
        cell.contentConfiguration = UIHostingConfiguration {
            AdaptiveTimelineCard(index: index, totalCount: totalCount) {
                TimelinePlaceholderView()
                    .padding(.vertical, 12)
                    .padding(.horizontal)
            }
        }
        .margins(.all, 0)
        .minSize(width: 0, height: 0)
    }

    private func configureHostingCell<V: View>(_ cell: UICollectionViewCell, @ViewBuilder content: () -> V) {
        let contentView = content()
        cell.contentConfiguration = UIHostingConfiguration {
            contentView
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        }
        .margins(.all, 0)
        .minSize(width: 0, height: 0)
    }

    private func configureErrorCell(_ cell: UICollectionViewCell) {
        guard let data = currentData, case .error(let errorState) = onEnum(of: data) else { return }
        cell.contentConfiguration = UIHostingConfiguration {
            ListErrorView(error: errorState.error) {
                errorState.onRetry()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        }
        .margins(.all, 0)
        .minSize(width: 0, height: 0)
    }

    private func configureFooterErrorCell(_ cell: UICollectionViewCell) {
        guard let success = currentSuccess else { return }
        if case .error(let error) = onEnum(of: success.appendState) {
            cell.contentConfiguration = UIHostingConfiguration {
                ListErrorView(error: error.error) {
                    success.retry()
                }
                .frame(maxWidth: .infinity, alignment: .center)
            }
            .margins(.all, 0)
            .minSize(width: 0, height: 0)
        }
    }

    // MARK: - Refresh

    @objc private func handleRefresh() {
        isUserRefreshing = true
        Task { @MainActor in
            if let refreshCallback {
                await refreshCallback()
            }
            isUserRefreshing = false
            if let data = currentData, !pagingIsRefreshing(data) {
                refreshControl.endRefreshing()
            }
        }
    }

    private func pagingIsRefreshing(_ data: PagingState<UiTimelineV2>) -> Bool {
        switch onEnum(of: data) {
        case .loading:
            return true
        case .success(let success):
            return success.isRefreshing
        default:
            return false
        }
    }

    // MARK: - State Update

    func update(data: PagingState<UiTimelineV2>) {
        currentData = data
        if case .success(let success) = onEnum(of: data) {
            currentSuccess = success
        } else {
            currentSuccess = nil
        }

        guard isViewLoaded else { return }

        syncRefreshControl(data: data)
        applySnapshot(data: data)
    }

    private func syncRefreshControl(data: PagingState<UiTimelineV2>) {
        let isRefreshing = pagingIsRefreshing(data)
        if isRefreshing {
            if !refreshControl.isRefreshing {
                refreshControl.beginRefreshing()
                shouldRevealRefreshControl = !isUserRefreshing
                revealRefreshControlIfNeeded()
            }
        } else if !isUserRefreshing {
            shouldRevealRefreshControl = false
            if refreshControl.isRefreshing {
                refreshControl.endRefreshing()
            }
        }
    }

    private func revealRefreshControlIfNeeded() {
        guard shouldRevealRefreshControl, refreshControl.isRefreshing else { return }

        // `beginRefreshing()` alone does not make the indicator visible.
        // Pull the collection view down far enough so the refresh control is revealed.
        let refreshHeight = max(refreshControl.bounds.height, 60)
        let targetOffsetY = -(collectionView.adjustedContentInset.top + refreshHeight)

        guard collectionView.contentOffset.y > targetOffsetY else {
            shouldRevealRefreshControl = false
            return
        }

        collectionView.setContentOffset(
            CGPoint(x: collectionView.contentOffset.x, y: targetOffsetY),
            animated: false
        )
        shouldRevealRefreshControl = false
    }

    private func applySnapshot(data: PagingState<UiTimelineV2>) {
        var snapshot = NSDiffableDataSourceSnapshot<Int, String>()
        var newIndexMap: [String: Int] = [:]
        var newRenderHashMap: [String: Int32] = [:]
        var itemIDs: [String] = []
        var footerIDs: [String] = []

        switch onEnum(of: data) {
        case .loading:
            snapshot.appendSections([Self.sectionMain])
            let items = (0..<5).map { "\(Self.placeholderPrefix)\($0)" }
            itemIDs = items
            snapshot.appendItems(items, toSection: Self.sectionMain)

        case .error:
            snapshot.appendSections([Self.sectionMain])
            itemIDs = [Self.errorID]
            snapshot.appendItems(itemIDs, toSection: Self.sectionMain)

        case .empty:
            snapshot.appendSections([Self.sectionMain])
            itemIDs = [Self.emptyID]
            snapshot.appendItems(itemIDs, toSection: Self.sectionMain)

        case .success(let success):
            snapshot.appendSections([Self.sectionMain])
            let itemCount = Int(success.itemCount)
            var items: [String] = []
            items.reserveCapacity(itemCount)
            for i in 0..<itemCount {
                let peeked = success.peek(index: Int32(i))
                let id = "\(Self.timelinePrefix)\(peeked?.itemKey ?? "idx_\(i)")"
                items.append(id)
                newIndexMap[id] = i
                if let peeked {
                    newRenderHashMap[id] = peeked.renderHash
                }
            }
            itemIDs = items
            snapshot.appendItems(items, toSection: Self.sectionMain)

            // Footer
            let footer = footerItemIDs(for: success)
            footerIDs = footer
            if !footer.isEmpty {
                snapshot.appendSections([Self.sectionFooter])
                snapshot.appendItems(footer, toSection: Self.sectionFooter)
            }
        }

        itemIndexMap = newIndexMap
        let newSignature = SnapshotSignature(itemIDs: itemIDs, footerIDs: footerIDs)
        let previousSignature = lastAppliedSignature

        if previousSignature?.itemIDs == newSignature.itemIDs,
           previousSignature?.footerIDs == newSignature.footerIDs {
            let changedIDs = itemIDs.filter { lastRenderHashMap[$0] != newRenderHashMap[$0] }
            lastRenderHashMap = newRenderHashMap
            reconfigureItems(changedIDs)
            return
        }

        if previousSignature?.itemIDs == newSignature.itemIDs {
            let changedIDs = itemIDs.filter { lastRenderHashMap[$0] != newRenderHashMap[$0] }
            applyFooterSnapshot(footerIDs: footerIDs, reconfigureIDs: changedIDs, data: data)
            lastAppliedSignature = newSignature
            lastRenderHashMap = newRenderHashMap
            return
        }

        // Reconfigure existing items so cells pick up data changes (e.g. like count)
        let existing = dataSource.snapshot().itemIdentifiers
        let newSet = Set(snapshot.itemIdentifiers)
        let toReconfigure = existing.filter { newSet.contains($0) }
        if !toReconfigure.isEmpty {
            snapshot.reconfigureItems(toReconfigure)
        }
        
        let shouldAnimateDifferences =
                    !pagingIsRefreshing(data) &&
                    !refreshControl.isRefreshing &&
                    !collectionView.isDragging &&
                    !collectionView.isDecelerating
        dataSource.apply(snapshot, animatingDifferences: shouldAnimateDifferences)
        lastAppliedSignature = newSignature
        lastRenderHashMap = newRenderHashMap
    }

    private func reconfigureItems(_ itemIDs: [String]) {
        guard !itemIDs.isEmpty else { return }
        var snapshot = dataSource.snapshot()
        let existingItems = Set(snapshot.itemIdentifiers)
        let reconfigureIDs = itemIDs.filter { existingItems.contains($0) }
        guard !reconfigureIDs.isEmpty else { return }
        snapshot.reconfigureItems(reconfigureIDs)
        dataSource.apply(snapshot, animatingDifferences: false)
    }

    private func applyFooterSnapshot(footerIDs: [String], reconfigureIDs: [String], data: PagingState<UiTimelineV2>) {
        var snapshot = dataSource.snapshot()
        let hasFooterSection = snapshot.sectionIdentifiers.contains(Self.sectionFooter)

        if hasFooterSection {
            snapshot.deleteSections([Self.sectionFooter])
        }

        if !footerIDs.isEmpty {
            snapshot.appendSections([Self.sectionFooter])
            snapshot.appendItems(footerIDs, toSection: Self.sectionFooter)
        }

        let existingItems = Set(snapshot.itemIdentifiers)
        let intersectedReconfigureIDs = reconfigureIDs.filter { existingItems.contains($0) }
        if !intersectedReconfigureIDs.isEmpty {
            snapshot.reconfigureItems(intersectedReconfigureIDs)
        }

        let shouldAnimateDifferences =
                    !pagingIsRefreshing(data) &&
                    !refreshControl.isRefreshing &&
                    !collectionView.isDragging &&
                    !collectionView.isDecelerating
        dataSource.apply(snapshot, animatingDifferences: shouldAnimateDifferences)
    }

    private func footerItemIDs(for success: PagingStateSuccess<UiTimelineV2>) -> [String] {
        switch onEnum(of: success.appendState) {
        case .error:
            return [Self.footerErrorID]
        case .loading:
            return [Self.footerLoadingID]
        case .notLoading(let notLoading):
            if notLoading.endOfPaginationReached {
                return [Self.footerEndID]
            }
            return []
        }
    }

    // MARK: - UICollectionViewDelegate

    func collectionView(_ collectionView: UICollectionView, willDisplay cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        guard let success = currentSuccess else { return }
        if let itemID = dataSource.itemIdentifier(for: indexPath),
           let index = itemIndexMap[itemID] {
            _ = success.get(index: Int32(index))
        }
    }

    // MARK: - UIScrollViewDelegate

    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        scrollingState.isScrolling = true
    }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        if !decelerate {
            scrollingState.isScrolling = false
        }
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        scrollingState.isScrolling = false
    }
}

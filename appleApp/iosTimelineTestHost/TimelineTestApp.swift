import UIKit
import CHTCollectionViewWaterfallLayout

@main
final class TimelineTestApp: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, configurationForConnecting session: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: session.role)
        configuration.delegateClass = TimelineTestScene.self
        return configuration
    }
}

final class TimelineTestScene: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options: UIScene.ConnectionOptions) {
        guard let scene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: scene)
        window.rootViewController = TimelineTestController()
        self.window = window
        window.makeKeyAndVisible()
    }
}

/// A real application window is required for native scroll/refresh animations and
/// pan/deceleration. A hostless XCTest window does not exercise those lifecycles.
private final class TimelineTestController: UIViewController, CHTCollectionViewDelegateWaterfallLayout {
    private let layout = CHTCollectionViewWaterfallLayout()
    private lazy var list = TimelineCollectionView(frame: .zero, collectionViewLayout: layout)
    private var dataSource: UICollectionViewDiffableDataSource<Int, Int>!
    private var heights: [Int: CGFloat] = [:]
    private var scrolling = false
    private var started = false
    private var failures: [String] = []
    private let scenario = ProcessInfo.processInfo.arguments.dropFirst().first ?? "refresh"

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        additionalSafeAreaInsets.top = 80
        layout.columnCount = scenario.contains("columns") ? 2 : 1
        layout.minimumInteritemSpacing = 0
        layout.minimumColumnSpacing = 8
        layout.sectionInset = UIEdgeInsets(top: 0, left: 16, bottom: 0, right: 16)
        list.delegate = self
        list.alwaysBounceVertical = true
        list.setTopContentInset(52)
        list.accessibilityIdentifier = "timeline-list"
        list.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(list)
        NSLayoutConstraint.activate([
            list.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            list.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            list.topAnchor.constraint(equalTo: view.topAnchor),
            list.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        let registration = UICollectionView.CellRegistration<UICollectionViewCell, Int> { cell, _, id in
            var content = UIListContentConfiguration.cell()
            content.text = "Item \(id)"
            cell.contentConfiguration = content
            cell.backgroundConfiguration = .listPlainCell()
        }
        dataSource = UICollectionViewDiffableDataSource<Int, Int>(collectionView: list) { view, path, id in
            view.dequeueConfiguredReusableCell(using: registration, for: path, item: id)
        }
        list.readingItemID = { [weak self] in self?.dataSource.itemIdentifier(for: $0).map(String.init) }
        list.readingIndexPath = { [weak self] in Int($0).flatMap { self?.dataSource.indexPath(for: $0) } }
        list.readingItemIDs = { [weak self] in self?.dataSource.snapshot().itemIdentifiers.map(String.init) ?? [] }
        list.onProgrammaticScrollBegan = { [weak self] in self?.scrolling = true }
        list.onProgrammaticScrollEnded = { [weak self] in self?.scrolling = false }
        list.refreshControl = UIRefreshControl()
        if scenario != "initial-refresh" { loadItems() }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !started else { return }
        started = true
        Task {
            await settle(200)
            switch scenario {
            case "scroll-to-top": await checkScrollToTop()
            case "drag", "deceleration", "refine-reading-item": await checkGesture()
            case let name where name.hasPrefix("snapshot-"): await checkSnapshotGesture()
            default: await checkRefresh()
            }
            let result = UILabel(frame: CGRect(x: 16, y: view.safeAreaInsets.top, width: view.bounds.width - 32, height: 80))
            result.accessibilityIdentifier = "timeline-result"
            result.numberOfLines = 0
            result.text = failures.isEmpty ? "PASS" : failures.joined(separator: "; ")
            view.addSubview(result)
        }
    }

    private func loadItems() {
        list.prepareForSnapshotChange()
        var snapshot = NSDiffableDataSourceSnapshot<Int, Int>()
        snapshot.appendSections([0])
        snapshot.appendItems(Array(0..<120))
        dataSource.apply(snapshot, animatingDifferences: false)
    }

    private func settle(_ milliseconds: Int) async {
        for _ in 0..<max(milliseconds / 10, 1) {
            view.layoutIfNeeded()
            list.layoutIfNeeded()
            try? await Task.sleep(for: .milliseconds(10))
        }
    }

    private func check(_ condition: Bool, _ message: String) {
        if !condition { failures.append(message) }
    }

    private func jump(to offset: CGFloat) {
        list.resetReadingPosition()
        list.setContentOffset(CGPoint(x: 0, y: offset), animated: false)
        list.layoutIfNeeded()
    }

    private func checkScrollToTop() async {
        jump(to: 3_000)
        await settle(100)
        // SwiftUI's tab reselection calls the animated setter directly. It does
        // not use the status-bar scrollViewShouldScrollToTop delegate entry.
        list.prepareForLayoutChange()
        list.setContentOffset(CGPoint(x: 0, y: -list.restingAdjustedTopInset), animated: true)
        await settle(100)
        check(list.contentOffset.y > 100, "scroll animation did not start")
        heights[0] = 220
        list.invalidateMeasuredHeights()
        await settle(800)
        check(abs(list.contentOffset.y + list.restingAdjustedTopInset) < 1, "height refinement interrupted scroll-to-top")
        check(!scrolling, "programmatic scroll never finished")
        // Once the explicit jump finishes, later prepends must preserve the old item.
        list.prepareForSnapshotChange()
        var snapshot = dataSource.snapshot()
        snapshot.insertItems([200], beforeItem: 0)
        await dataSource.apply(snapshot, animatingDifferences: false)
        await settle(100)
        let frame = layout.layoutAttributesForItem(at: dataSource.indexPath(for: 0)!)!.frame
        check(abs(frame.minY - list.contentOffset.y - list.restingAdjustedTopInset) < 1, "prepend lost the reading item after scroll-to-top")
    }

    private func checkGesture() async {
        let refineReadingItem = scenario == "refine-reading-item"
        if refineReadingItem {
            heights[20] = 600
            list.invalidateMeasuredHeights()
            let frame = layout.layoutAttributesForItem(at: dataSource.indexPath(for: 20)!)!.frame
            jump(to: frame.minY + 500 - list.restingAdjustedTopInset)
        } else {
            jump(to: layout.collectionViewContentSize.height - list.bounds.height + list.adjustedContentInset.bottom)
        }
        await settle(100)
        let decelerating = scenario.contains("deceleration")
        await waitForGesture()
        check(decelerating ? list.isDecelerating : list.isDragging, "native gesture was not observed")
        guard let id = list.captureReadingPosition()?.itemID.flatMap(Int.init),
              let path = dataSource.indexPath(for: id), id > 0,
              let oldFrame = layout.layoutAttributesForItem(at: path)?.frame else {
            failures.append("missing reading item during gesture")
            return
        }
        let oldScreenY = oldFrame.minY - list.contentOffset.y
        if refineReadingItem {
            heights[id] = 120
        } else {
            heights[id - 1] = itemHeight(id - 1) + 120
        }
        list.invalidateMeasuredHeights()
        list.layoutIfNeeded()
        let newScreenY = layout.layoutAttributesForItem(at: path)!.frame.minY - list.contentOffset.y
        if refineReadingItem {
            check(list.captureReadingPosition()?.itemID == String(id), "refinement hid the reading item during the pan")
            check(abs(newScreenY - list.restingAdjustedTopInset + 119) < 1, "unreachable estimated offset was not clamped to the reading item")
        } else {
            check(abs(newScreenY - oldScreenY) < 1, "height refinement moved reading item by \(newScreenY - oldScreenY)pt")
        }
        check(decelerating ? list.isDecelerating : list.isDragging, "height compensation cancelled gesture")
        if decelerating {
            let offset = list.contentOffset.y
            await settle(40)
            check(abs(list.contentOffset.y - offset) > 1, "height compensation stopped momentum")
        }
    }

    private func waitForGesture() async {
        let ready = UILabel(frame: CGRect(x: 16, y: 0, width: 200, height: 20))
        ready.text = "Ready"
        ready.accessibilityIdentifier = "gesture-ready"
        view.addSubview(ready)
        let decelerating = scenario.contains("deceleration")
        for _ in 0..<1500 {
            if decelerating ? list.isDecelerating : list.isDragging { break }
            try? await Task.sleep(for: .milliseconds(10))
        }
    }

    private func checkSnapshotGesture() async {
        let frame = layout.layoutAttributesForItem(at: dataSource.indexPath(for: 80)!)!.frame
        jump(to: frame.minY + 40 - list.restingAdjustedTopInset)
        await settle(100)
        await waitForGesture()
        let decelerating = scenario.contains("deceleration")
        check(decelerating ? list.isDecelerating : list.isDragging, "native gesture was not observed")
        for change in ["append", "insert", "move", "delete", "delete-reading-item", "measure", "delete-tail"] {
            // Delayed measurement must use the user's current position.
            if change == "measure" { await settle(40) }
            guard let id = list.captureReadingPosition()?.itemID.flatMap(Int.init),
                  let path = dataSource.indexPath(for: id),
                  let oldFrame = layout.layoutAttributesForItem(at: path)?.frame else {
                failures.append("missing reading item before \(change)")
                return
            }
            let oldScreenY = oldFrame.minY - list.contentOffset.y
            var snapshot = dataSource.snapshot()
            var expectedID = id
            switch change {
            case "append": snapshot.appendItems([200, 201])
            case "insert": snapshot.insertItems([202], beforeItem: 0)
            case "move": snapshot.moveItem(1, afterItem: 201)
            case "delete": snapshot.deleteItems([2])
            case "measure": heights[0] = itemHeight(0) + 120
            case "delete-tail":
                snapshot.deleteItems(Array(snapshot.itemIdentifiers.dropFirst(snapshot.indexOfItem(id)! + 1)))
            default:
                expectedID = snapshot.itemIdentifiers[snapshot.indexOfItem(id)! + 1]
                snapshot.deleteItems([id])
            }
            if change == "measure" {
                list.invalidateMeasuredHeights()
            } else {
                list.prepareForSnapshotChange()
                list.performUpdatesPreservingReadingPosition(keepingItemIDs: Set(snapshot.itemIdentifiers.map(String.init))) {
                    dataSource.apply(snapshot, animatingDifferences: false, completion: nil)
                }
            }
            list.layoutIfNeeded()
            let newFrame = layout.layoutAttributesForItem(at: dataSource.indexPath(for: expectedID)!)!.frame
            let expectedY = max(oldScreenY, list.restingAdjustedTopInset + 1 - newFrame.height)
            if change == "delete-tail" {
                let bottom = max(-list.adjustedContentInset.top, list.contentSize.height - list.bounds.height + list.adjustedContentInset.bottom)
                check(abs(list.contentOffset.y - bottom) < 1, "removing the tail left a blank viewport")
                check(newFrame.maxY > list.contentOffset.y + list.restingAdjustedTopInset, "removing the tail hid the reading item")
            } else {
                check(abs(newFrame.minY - list.contentOffset.y - expectedY) < 1,
                      "\(change) moved reading item by \(newFrame.minY - list.contentOffset.y - expectedY)pt")
            }
            check(decelerating ? list.isDecelerating : list.isDragging, "\(change) cancelled gesture")
            check(!list.hasReadingPosition, "\(change) left a stale bookmark")
        }
        if decelerating {
            let offset = list.contentOffset.y
            await settle(40)
            check(abs(list.contentOffset.y - offset) > 1, "snapshot compensation stopped momentum")
        }
    }

    private func checkRefresh() async {
        let baseline = list.restingAdjustedTopInset
        jump(to: -baseline)
        if scenario == "refresh-without-animations" {
            UIView.performWithoutAnimation { list.beginRefreshing(revealingIndicator: true) }
        } else {
            list.beginRefreshing(revealingIndicator: true)
        }
        if scenario == "initial-refresh" {
            await settle(100)
            loadItems()
        }
        await settle(800)
        let control = list.refreshControl!
        func animating(_ layer: CALayer) -> Bool {
            !(layer.animationKeys() ?? []).isEmpty || (layer.sublayers ?? []).contains(where: animating)
        }
        check(control.isRefreshing && animating(control.layer), "refresh indicator is static")
        check(abs(list.restingAdjustedTopInset - baseline) < 1, "refresh changed the resting inset")
        let frame = layout.layoutAttributesForItem(at: dataSource.indexPath(for: 0)!)!.frame
        let gap = frame.minY - list.contentOffset.y - baseline - control.bounds.height
        check(abs(gap) < 1, "extra blank below refresh control: \(gap)pt")
        list.endRefreshing()
        await settle(500)
        check(abs(list.contentOffset.y + baseline) < 1, "refresh did not return to resting top")
    }

    private func itemHeight(_ id: Int) -> CGFloat { heights[id] ?? CGFloat(120 + id % 7 * 17) }

    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout,
                        sizeForItemAt indexPath: IndexPath) -> CGSize {
        CGSize(width: layout.itemWidth(inSection: indexPath.section), height: itemHeight(dataSource.itemIdentifier(for: indexPath)!))
    }

    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        list.endProgrammaticScrolling()
        scrolling = true
        list.interruptRefreshForScrolling()
    }

    func scrollViewShouldScrollToTop(_ scrollView: UIScrollView) -> Bool {
        list.beginProgrammaticScrolling()
        scrolling = true
        list.interruptRefreshForScrolling()
        return true
    }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        if !decelerate { scrolling = false }
    }
    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) { scrolling = false }
    func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
        list.endProgrammaticScrolling()
        scrolling = false
    }
    func scrollViewDidScrollToTop(_ scrollView: UIScrollView) { scrolling = false }
}

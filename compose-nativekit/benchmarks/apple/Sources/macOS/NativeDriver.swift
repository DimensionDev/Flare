import AppKit

@MainActor final class NativeDriver: NSObject, BenchmarkDriver, NSCollectionViewDataSource, NSCollectionViewDelegateFlowLayout {
    let model: NativeModel
    let scrollView = NSScrollView(frame: NSRect(x: 0, y: 0, width: 390, height: 780))
    private let collection = FlippedCollection(frame: NSRect(x: 0, y: 0, width: 390, height: 780))
    private let flow = NSCollectionViewFlowLayout()
    var view: NSView { scrollView }
    private(set) var created = 0
    var active: Int { collection.visibleItems().count }
    var offset: CGFloat { model.scenario.horizontal ? scrollView.contentView.bounds.minX : scrollView.contentView.bounds.minY }

    init(_ scenario: Scenario) {
        model = NativeModel(scenario)
        super.init()
        flow.scrollDirection = scenario.horizontal ? .horizontal : .vertical
        flow.minimumLineSpacing = 4
        flow.minimumInteritemSpacing = 0
        collection.collectionViewLayout = flow
        collection.dataSource = self
        collection.delegate = self
        collection.isSelectable = false
        collection.backgroundColors = [.clear]
        for type in 0..<3 { collection.register(NativeItem.self, forItemWithIdentifier: NSUserInterfaceItemIdentifier("item-\(type)")) }
        scrollView.documentView = collection
        scrollView.drawsBackground = false
        scrollView.hasVerticalScroller = !scenario.horizontal
        scrollView.hasHorizontalScroller = scenario.horizontal
        scrollView.autohidesScrollers = true
        scrollView.scrollerStyle = .overlay
    }

    func mount() { collection.reloadData() }
    func layout() { scrollView.layoutSubtreeIfNeeded(); collection.layoutSubtreeIfNeeded() }
    func physicalScroll(_ active: Bool) {}
    func dispose() { collection.dataSource = nil; collection.delegate = nil }
    func scroll(_ offset: CGFloat) {
        scrollView.contentView.setBoundsOrigin(model.scenario.horizontal ? NSPoint(x: offset, y: 0) : NSPoint(x: 0, y: offset))
        scrollView.reflectScrolledClipView(scrollView.contentView)
    }
    func items() -> [VisibleItem] {
        collection.visibleItems().compactMap { item in
            guard let path = collection.indexPath(for: item) else { return nil }
            let frame = item.view.frame
            let start = model.scenario.horizontal ? frame.minX : frame.minY
            let extent = model.scenario.horizontal ? frame.width : frame.height
            let viewport = model.scenario.horizontal ? scrollView.contentView.bounds.width : scrollView.contentView.bounds.height
            guard start + extent > offset && start < offset + viewport else { return nil }
            return VisibleItem(index: path.item, key: model.key(path.item), offset: start - offset, extent: extent)
        }.sorted { $0.index < $1.index }
    }
    func ready() -> Bool {
        collection.numberOfItems(inSection: 0) == model.count && !items().isEmpty && collection.visibleItems().allSatisfy { item in
            guard let path = collection.indexPath(for: item), let item = item as? NativeItem else { return false }
            let extent = model.scenario.horizontal ? item.view.frame.width : item.view.frame.height
            return abs(extent - model.extent(path.item)) < 1 && item.labels.first?.stringValue == model.strings(path.item).first
        }
    }
    func jump(_ index: Int) {
        layout()
        if let frame = flow.layoutAttributesForItem(at: IndexPath(item: index, section: 0))?.frame {
            scroll((model.scenario.horizontal ? frame.minX : frame.minY) + 13)
        }
    }
    func prepend() {
        let anchor = items().first!
        NSAnimationContext.runAnimationGroup { context in
            context.duration = 0
            context.allowsImplicitAnimation = false
            collection.performBatchUpdates {
                model.prefix += 20
                collection.insertItems(at: Set((0..<20).map { IndexPath(item: $0, section: 0) }))
                flow.invalidateLayout()
                flow.prepare()
                collection.setFrameSize(flow.collectionViewContentSize)
                if let frame = flow.layoutAttributesForItem(at: IndexPath(item: anchor.index + 20, section: 0))?.frame {
                    scroll((model.scenario.horizontal ? frame.minX : frame.minY) - anchor.offset)
                }
            }
        }
    }
    func resize() {
        let anchor = items().first!
        if model.expandedKey == nil { model.expandedKey = anchor.key }
        model.expanded.toggle()
        for case let item as NativeItem in collection.visibleItems() {
            if let path = collection.indexPath(for: item) { item.configure(model, path.item) }
        }
        flow.invalidateLayout()
    }
    func collectionView(_ collectionView: NSCollectionView, numberOfItemsInSection section: Int) -> Int { model.count }
    func collectionView(_ collectionView: NSCollectionView, itemForRepresentedObjectAt indexPath: IndexPath) -> NSCollectionViewItem {
        let type = (model.key(indexPath.item) % 3 + 3) % 3
        let item = collectionView.makeItem(withIdentifier: NSUserInterfaceItemIdentifier("item-\(type)"), for: indexPath) as! NativeItem
        if item.labels.isEmpty { created += 1 }
        item.configure(model, indexPath.item)
        return item
    }
    func collectionView(_ collectionView: NSCollectionView, layout collectionViewLayout: NSCollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> NSSize {
        model.scenario.horizontal ? NSSize(width: model.extent(indexPath.item), height: 780) : NSSize(width: 390, height: model.extent(indexPath.item))
    }
}

@MainActor private final class FlippedCollection: NSCollectionView { override var isFlipped: Bool { true } }

@MainActor private final class NativeItem: NSCollectionViewItem {
    var labels: [NSTextField] = []
    private var extent: NSLayoutConstraint?
    override func loadView() { view = NSStackView(frame: .zero) }

    func configure(_ model: NativeModel, _ index: Int) {
        let strings = model.strings(index)
        if labels.isEmpty {
            labels = strings.map { _ in
                let label = NSTextField(labelWithString: "")
                label.maximumNumberOfLines = 0
                label.lineBreakMode = .byWordWrapping
                label.usesSingleLineMode = false
                return label
            }
            let root = view as! NSStackView
            root.orientation = model.scenario.horizontal ? .horizontal : .vertical
            root.alignment = model.scenario.horizontal ? .top : .leading
            root.spacing = 0
            let content: NSView
            if model.scenario.cards {
                let top = NSStackView(views: Array(labels[0...1])); top.spacing = 8; top.alignment = .centerY
                let bottom = NSStackView(views: Array(labels[3...5])); bottom.spacing = 12; bottom.alignment = .centerY
                let column = NSStackView(views: [top, labels[2], bottom])
                column.orientation = .vertical; column.alignment = .leading; column.spacing = 6
                labels[2].widthAnchor.constraint(equalTo: column.widthAnchor).isActive = true
                content = column
            } else { content = labels[0] }
            root.addArrangedSubview(content)
            extent = model.scenario.horizontal ? content.widthAnchor.constraint(equalToConstant: model.extent(index)) : content.heightAnchor.constraint(equalToConstant: model.extent(index))
            extent?.isActive = true
            let cross = model.scenario.horizontal ? content.heightAnchor.constraint(equalTo: root.heightAnchor) : content.widthAnchor.constraint(equalTo: root.widthAnchor)
            cross.priority = NSLayoutConstraint.Priority(999)
            cross.isActive = true
        }
        zip(labels, strings).forEach { $0.stringValue = $1 }
        extent?.constant = model.extent(index)
        view.needsLayout = true
    }
}

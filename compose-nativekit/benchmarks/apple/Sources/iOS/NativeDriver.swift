import UIKit

@MainActor final class NativeDriver: NSObject, BenchmarkDriver, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {
    let model: NativeModel
    let collection: UICollectionView
    private let flow = UICollectionViewFlowLayout()
    var view: UIView { collection }
    private(set) var created = 0
    var active: Int { collection.visibleCells.count }
    var offset: CGFloat { model.scenario.horizontal ? collection.contentOffset.x : collection.contentOffset.y }

    init(_ scenario: Scenario) {
        model = NativeModel(scenario)
        flow.scrollDirection = scenario.horizontal ? .horizontal : .vertical
        flow.minimumLineSpacing = 4
        flow.minimumInteritemSpacing = 0
        collection = UICollectionView(frame: CGRect(x: 0, y: 0, width: 390, height: 780), collectionViewLayout: flow)
        super.init()
        collection.dataSource = self
        collection.delegate = self
        collection.isPrefetchingEnabled = false
        collection.allowsSelection = false
        collection.contentInsetAdjustmentBehavior = .never
        collection.backgroundColor = .clear
        for type in 0..<3 { collection.register(NativeCell.self, forCellWithReuseIdentifier: "item-\(type)") }
    }

    func mount() { collection.reloadData() }
    func layout() { collection.layoutIfNeeded() }
    func physicalScroll(_ active: Bool) {}
    func dispose() { collection.dataSource = nil; collection.delegate = nil }
    func scroll(_ offset: CGFloat) {
        collection.setContentOffset(model.scenario.horizontal ? CGPoint(x: offset, y: 0) : CGPoint(x: 0, y: offset), animated: false)
    }
    func items() -> [VisibleItem] {
        collection.visibleCells.compactMap { cell in
            guard let path = collection.indexPath(for: cell) else { return nil }
            let start = model.scenario.horizontal ? cell.frame.minX : cell.frame.minY
            let extent = model.scenario.horizontal ? cell.frame.width : cell.frame.height
            let viewport = model.scenario.horizontal ? collection.bounds.width : collection.bounds.height
            guard start + extent > offset && start < offset + viewport else { return nil }
            return VisibleItem(index: path.item, key: model.key(path.item), offset: start - offset, extent: extent)
        }.sorted { $0.index < $1.index }
    }
    func ready() -> Bool {
        collection.numberOfItems(inSection: 0) == model.count && !items().isEmpty && collection.visibleCells.allSatisfy { cell in
            guard let path = collection.indexPath(for: cell), let cell = cell as? NativeCell else { return false }
            let extent = model.scenario.horizontal ? cell.frame.width : cell.frame.height
            return abs(extent - model.extent(path.item)) < 1 && cell.labels.first?.text == model.strings(path.item).first
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
        UIView.performWithoutAnimation {
            collection.performBatchUpdates {
                model.prefix += 20
                collection.insertItems(at: (0..<20).map { IndexPath(item: $0, section: 0) })
                flow.invalidateLayout()
                flow.prepare()
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
        for case let cell as NativeCell in collection.visibleCells {
            if let path = collection.indexPath(for: cell) { cell.configure(model, path.item) }
        }
        flow.invalidateLayout()
    }
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int { model.count }
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let type = (model.key(indexPath.item) % 3 + 3) % 3
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "item-\(type)", for: indexPath) as! NativeCell
        if cell.labels.isEmpty { created += 1 }
        cell.configure(model, indexPath.item)
        return cell
    }
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        model.scenario.horizontal ? CGSize(width: model.extent(indexPath.item), height: 780) : CGSize(width: 390, height: model.extent(indexPath.item))
    }
}

@MainActor private final class NativeCell: UICollectionViewCell {
    var labels: [UILabel] = []
    private var extent: NSLayoutConstraint?

    func configure(_ model: NativeModel, _ index: Int) {
        let strings = model.strings(index)
        if labels.isEmpty {
            labels = strings.map { _ in let label = UILabel(); label.numberOfLines = 0; return label }
            let root = UIStackView(frame: contentView.bounds)
            root.axis = model.scenario.horizontal ? .horizontal : .vertical
            root.alignment = .fill
            root.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            contentView.addSubview(root)
            let content: UIView
            if model.scenario.cards {
                let top = UIStackView(arrangedSubviews: Array(labels[0...1])); top.spacing = 8; top.alignment = .center
                let bottom = UIStackView(arrangedSubviews: Array(labels[3...5])); bottom.spacing = 12; bottom.alignment = .center
                let column = UIStackView(arrangedSubviews: [top, labels[2], bottom])
                column.axis = .vertical; column.alignment = .leading; column.spacing = 6
                labels[2].widthAnchor.constraint(equalTo: column.widthAnchor).isActive = true
                content = column
            } else { content = labels[0] }
            root.addArrangedSubview(content)
            extent = model.scenario.horizontal ? content.widthAnchor.constraint(equalToConstant: model.extent(index)) : content.heightAnchor.constraint(equalToConstant: model.extent(index))
            extent?.isActive = true
        }
        zip(labels, strings).forEach { $0.text = $1 }
        extent?.constant = model.extent(index)
        setNeedsLayout()
    }
}

//
// ZJFlexibleLayout.swift
//
//
// Created by 张智杰 on 2016/12/9.

import UIKit

open class ZJFlexibleLayout: UICollectionViewLayout {
    open weak var dataSourceDelegate: ZJFlexibleDataSource?

    open var collectionHeaderView: UIView? {
        willSet {
            collectionHeaderView?.removeFromSuperview()
        }
        didSet {
            collectionView?.reloadData()
        }
    }

    // 暂存
    var layoutDict: [IndexPath: UICollectionViewLayoutAttributes] = [:]
    var layoutHeaderViewInfo: [UICollectionViewLayoutAttributes] = []

    // 坐标寄存器
    var colHeights: [ColPosition] = []

    public init(delegate: ZJFlexibleDataSource) {
        dataSourceDelegate = delegate
        super.init()
    }

    public required init?(coder aDecoder: NSCoder) {
        super.init(coder: aDecoder)
    }

    override open var collectionViewContentSize: CGSize {
        if var max = colHeights.last?.maxY {
            if let delegate = dataSourceDelegate {
                max += delegate.sectionInsets(at: colHeights.count - 1).bottom
            }
            return CGSize(width: kScreenWidth, height: max)
        }
        return CGSize.zero
    }

    override open func prepare() {
        // 每次reloadData后需要layout
        layoutInit()

        if let sectionNum = collectionView?.numberOfSections {
            for section in 0 ..< sectionNum {
                guard let delegate = dataSourceDelegate else { continue }

                // 取(前一个section的Y坐标+当前section的高度)为当前section的初始坐标
                let originH = collectionHeaderView?.bounds.size.height ?? 0
                let preSectionH = section == 0 ? originH : colHeights[section - 1].maxY
                let preSectionInsetBottom = section == 0 ? 0 : delegate.sectionInsets(at: section - 1).bottom
                let currentSectionHeaderY = preSectionH + preSectionInsetBottom - (section == 0 ? 0 : delegate.spaceOfCells(at: section - 1))
                let headerSize = delegate.sizeOfHeader(at: section)
                let headerX = (kScreenWidth - headerSize.width) / 2
                let headerH = headerSize.height

                // 拼接header 的layoutAttributes
                let headerAttributes = UICollectionViewLayoutAttributes(forSupplementaryViewOfKind: UICollectionView.elementKindSectionHeader, with: IndexPath(item: 0, section: section))
                headerAttributes.frame = CGRect(x: headerX, y: currentSectionHeaderY, width: headerSize.width, height: headerSize.height)
                layoutHeaderViewInfo.append(headerAttributes)

                // 每个section开始计算height清零
                var rowSavers: [ColY] = []
                for index in 0 ..< delegate.numberOfCols(at: section) {
                    // 当前section中cell的初始Y：sectionY + sectionHeaderHeight + insetTop
                    let currentSectionY = currentSectionHeaderY + headerH + delegate.sectionInsets(at: section).top
                    rowSavers.append((index, currentSectionY))
                }
                colHeights.append(ColPosition(colYs: rowSavers))

                // 拼接cell的layoutAttributes
                if let itemNum = collectionView?.numberOfItems(inSection: section) {
                    for item in 0 ..< itemNum {
                        let indexPath = IndexPath(item: item, section: section)
                        let itemAttributes = UICollectionViewLayoutAttributes(forCellWith: indexPath)
                        itemAttributes.frame = frameForCellAtIndexPath(indexPath)
                        layoutDict.updateValue(itemAttributes, forKey: IndexPath(item: item, section: section))
                    }
                }
            }
        }
    }

    func frameForCellAtIndexPath(_ indexPath: IndexPath) -> CGRect {
        if let delegate = dataSourceDelegate {
            // 计算cell宽度
            let sectionInsets = delegate.sectionInsets(at: indexPath.section)

            let space = delegate.spaceOfCells(at: indexPath.section)
            let colNum = CGFloat(delegate.numberOfCols(at: indexPath.section))
            let spaceNum = CGFloat(max(0, delegate.numberOfCols(at: indexPath.section) - 1))
            let cellWidth = (kScreenWidth - sectionInsets.left - sectionInsets.right - space * spaceNum) / colNum

            let itemSize = delegate.sizeOfItemAtIndexPath(at: indexPath)
            var originX: CGFloat = 0.0
            var originY: CGFloat = 0.0
            var cellHeight: CGFloat = 0.0

            // 根据图片宽高计算cell高度
            if itemSize.width > 0 {
                cellHeight = cellWidth * itemSize.height / itemSize.width
            }

            cellHeight += delegate.heightOfAdditionalContent(at: indexPath)

            // 找到最小高度
            if var min = colHeights[indexPath.section].colYs.min(by: { $0.colY < $1.colY }) {
                originX = sectionInsets.left + cellWidth * CGFloat(min.index) + space * CGFloat(min.index)
                originY = min.colY
                min.colY += cellHeight + space

                colHeights[indexPath.section].colYs.remove(at: min.index)
                colHeights[indexPath.section].colYs.insert(min, at: min.index)
            }

            return CGRect(x: originX, y: originY, width: cellWidth, height: cellHeight)
        }
        return CGRect.zero
    }

    func layoutInit() {
        colHeights.removeAll()
        layoutDict.removeAll()
        layoutHeaderViewInfo.removeAll()

        // 初始化headerView, Y为0
        guard let headerView = collectionHeaderView else { return }
        let headerWidth = headerView.bounds.size.width
        let headerX = (kScreenWidth - headerWidth) / 2
        headerView.frame = CGRect(x: headerX, y: 0, width: headerWidth, height: headerView.bounds.size.height)
        collectionView?.addSubview(headerView)
    }

    override open func layoutAttributesForItem(at indexPath: IndexPath) -> UICollectionViewLayoutAttributes? {
        layoutDict[indexPath]
    }

    override open func layoutAttributesForElements(in rect: CGRect) -> [UICollectionViewLayoutAttributes]? {
        var result: [UICollectionViewLayoutAttributes] = []
        for attribute in layoutDict.values {
            if attribute.frame.intersects(rect) {
                result.append(attribute)
            }
        }
        for attribute in layoutHeaderViewInfo {
            if attribute.frame.intersects(rect) {
                result.append(attribute)
            }
        }
        return result
    }

    override open func layoutAttributesForSupplementaryView(ofKind _: String, at indexPath: IndexPath) -> UICollectionViewLayoutAttributes? {
        if indexPath.section >= layoutHeaderViewInfo.count {
            return nil
        }
        return layoutHeaderViewInfo[indexPath.section]
    }
}

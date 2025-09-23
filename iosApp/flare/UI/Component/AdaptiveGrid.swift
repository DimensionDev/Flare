import SwiftUI

struct AdaptiveGrid: Layout {

    public var singleFollowsImageAspect: Bool
    public var spacing: CGFloat
    public var maxColumns: Int

    public init(
        singleFollowsImageAspect: Bool = true,
        spacing: CGFloat = 4,
        maxColumns: Int = 3
    ) {
        self.singleFollowsImageAspect = singleFollowsImageAspect
        self.spacing = spacing
        self.maxColumns = max(1, maxColumns)
    }

    public struct Cache {}
    public func makeCache(subviews: Subviews) -> Cache { Cache() }
    public func updateCache(_ cache: inout Cache, subviews: Subviews) {}

    public func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout Cache
    ) -> CGSize {
        let count = subviews.count
        guard count > 0 else { return .zero }

        let defaultWidth: CGFloat = 320
        var width = proposal.width ?? defaultWidth

        switch count {
        case 1:
            let ratio = aspectForSingle(subviews: subviews)
            if let height = proposal.height, proposal.width == nil { width = height * ratio }
            return CGSize(width: width, height: width / ratio)

        case 2, 3, 4:
            let ratio: CGFloat = 16.0 / 9.0
            if let height = proposal.height, proposal.width == nil { width = height * ratio }
            return CGSize(width: width, height: width / ratio)

        default:
            let cols = min(maxColumns, 3)
            let rowsTotal = Int(ceil(Double(count) / Double(cols)))
            if let height = proposal.height, proposal.width == nil {
                let a = CGFloat(rowsTotal) / CGFloat(cols)
                let b = spacing * (CGFloat(rowsTotal) / CGFloat(cols) - 1)
                width = a > 0 ? max(1, (height - b) / a) : defaultWidth
            }
            let height = heightForGridFillLastRow(width: width, count: count, cols: cols, spacing: spacing)
            return CGSize(width: width, height: height)
        }
    }

    public func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout Cache
    ) {
        let count = subviews.count
        guard count > 0 else { return }

        let size = sizeThatFits(
            proposal: ProposedViewSize(width: bounds.width, height: bounds.height),
            subviews: subviews,
            cache: &cache
        )
        let width = size.width
        let height = size.height
        let spacing = spacing
        let origin = CGPoint(x: bounds.minX, y: bounds.minY)

        func place(_ i: Int, x: CGFloat, y: CGFloat, width: CGFloat, height: CGFloat) {
            guard subviews.indices.contains(i) else { return }
            subviews[i].place(
                at: CGPoint(x: origin.x + x, y: origin.y + y),
                anchor: .topLeading,
                proposal: ProposedViewSize(width: width, height: height)
            )
        }

        switch count {
        case 1:
            let ratio = aspectForSingle(subviews: subviews)
            place(0, x: 0, y: 0, width: width, height: width / ratio)

        case 2:
            let cellW = (width - spacing) / 2
            place(0, x: 0, y: 0, width: cellW, height: height)
            place(1, x: cellW + spacing, y: 0, width: cellW, height: height)

        case 3:
            let halfW = (width - spacing) / 2
            let rightH = (height - spacing) / 2
            place(0, x: 0, y: 0, width: halfW, height: height)
            place(1, x: halfW + spacing, y: 0, width: halfW, height: rightH)
            place(2, x: halfW + spacing, y: rightH + spacing, width: halfW, height: rightH)

        case 4:
            let cellW = (width - spacing) / 2
            let cellH = (height - spacing) / 2
            place(0, x: 0, y: 0, width: cellW, height: cellH)
            place(1, x: cellW + spacing, y: 0, width: cellW, height: cellH)
            place(2, x: 0, y: cellH + spacing, width: cellW, height: cellH)
            place(3, x: cellW + spacing, y: cellH + spacing, width: cellW, height: cellH)

        default:
            let cols = min(maxColumns, 3)
            let fullRows = count / cols
            let rem = count % cols

            let columnWidth = (width - CGFloat(cols - 1) * spacing) / CGFloat(cols)   // 行高
            var idx = 0
            var y: CGFloat = 0

            for r in 0..<fullRows {
                for c in 0..<cols {
                    let x = CGFloat(c) * (columnWidth + spacing)
                    place(idx, x: x, y: y, width: columnWidth, height: columnWidth)
                    idx += 1
                }
                y += columnWidth
                if r < fullRows - 1 || rem > 0 { y += spacing }
            }

            if rem > 0 {
                let tailW = (width - CGFloat(rem - 1) * spacing) / CGFloat(rem)
                for c in 0..<rem {
                    let x = CGFloat(c) * (tailW + spacing)
                    place(idx, x: x, y: y, width: tailW, height: columnWidth)
                    idx += 1
                }
            }
        }
    }

    private func aspectForSingle(subviews: Subviews) -> CGFloat {
        if singleFollowsImageAspect {
            let ideal = subviews[0].sizeThatFits(.unspecified)
            if ideal.width > 0, ideal.height > 0 { return max(0.01, ideal.width / ideal.height) }
            return 1
        } else {
            return 16.0 / 9.0
        }
    }

    private func heightForGridFillLastRow(width width: CGFloat, count n: Int, cols: Int, spacing s: CGFloat) -> CGFloat {
        let rowsTotal = Int(ceil(Double(n) / Double(cols)))
        let columnWidth = (width - CGFloat(cols - 1) * s) / CGFloat(cols)
        return CGFloat(rowsTotal) * columnWidth + CGFloat(max(0, rowsTotal - 1)) * s
    }
}

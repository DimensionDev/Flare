import SwiftUI

struct AdaptiveGrid: Layout {
    var spacing: CGFloat = 4
    
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let proposedWidth = proposal.replacingUnspecifiedDimensions().width
        
        if subviews.count == 1 {
            let placeable = subviews[0].sizeThatFits(proposal)
            return CGSize(width: placeable.width, height: placeable.height)
        } else {
            let columns = max(Int(ceil(sqrt(Double(subviews.count)))), 1)
            let itemSize = (proposedWidth - spacing * CGFloat(columns - 1)) / CGFloat(columns)
            let rows = Int(ceil(Double(subviews.count) / Double(columns)))
            let width = proposedWidth
            let height = (CGFloat(rows) * itemSize) + (CGFloat((rows - 1)) * spacing)
            return CGSize(width: width, height: height)
        }
    }
    
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        if subviews.count == 1 {
            subviews[0].place(at: .init(x: bounds.minX, y: bounds.minY), proposal: .init(width: bounds.width, height: bounds.height))
        } else {
            let space = spacing
            let columns = max(Int(ceil(sqrt(Double(subviews.count)))), 1)
            let itemSize = (bounds.width - space * CGFloat(columns - 1)) / CGFloat(columns)
            var row = 0
            var column = 0
            for subview in subviews {
                let x = CGFloat(column) * itemSize + CGFloat(column) * space
                let y = CGFloat(row) * itemSize + CGFloat(row) * space
                subview.place(at: .init(x: bounds.minX + x, y: bounds.minY + y), proposal: .init(width: itemSize, height: itemSize))
                column += 1
                if column == columns {
                    column = 0
                    row += 1
                }
            }
        }
    }
}

#Preview {
    AdaptiveGrid {
        ForEach(0..<5) { index in
            Rectangle()
                .fill(Color.red)
                .overlay(Text("\(index)").foregroundColor(.white))
        }
    }.border(Color.black)
}

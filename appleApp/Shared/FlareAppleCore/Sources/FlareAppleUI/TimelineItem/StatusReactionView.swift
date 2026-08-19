import SwiftUI
import KotlinSharedUI
import FlareAppleCore

struct StatusReactionView: View {
    @Environment(\.openURL) private var openURL
    let data: [UiTimelineV2.PostEmojiReaction]
    let isDetail: Bool
    var body: some View {
        if isDetail {
            WrappedHStack {
                reactionContent
            }
        } else {
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    reactionContent
                }
            }
            .scrollIndicators(.hidden)
        }
    }
    
    var reactionContent: some View {
        ForEach(data, id: \.name) { item in
            Button {
                item.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
            } label: {
                HStack(spacing: 4) {
                    if item.isUnicode {
                        Text(item.name)
                    } else {
                        NetworkImage(data: item.url)
                            .scaledToFit()
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    Text(item.count.humanized)
                }
                .foregroundStyle(Color.flareLabel)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(
                    item.me
                        ? Color.accentColor.opacity(0.18)
                        : Color.flareSecondarySystemGroupedBackground,
                    in: Capsule()
                )
                .overlay {
                    Capsule()
                        .stroke(item.me ? Color.accentColor : Color.clear, lineWidth: 1)
                }
            }
            .buttonStyle(.plain)
        }
    }
}



public struct WrappedHStack: Layout {
    private let horizontalSpacing: CGFloat
    
    private let verticalSpacing: CGFloat
    

    public init(
        horizontalSpacing: CGFloat = 8,
        verticalSpacing: CGFloat = 8,
    ) {
        self.horizontalSpacing = horizontalSpacing
        self.verticalSpacing = verticalSpacing
    }
    
    public func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        guard !subviews.isEmpty else {
            return .zero
        }

        var width: CGFloat = 0
        var height: CGFloat = 0
        var rowHeight: CGFloat = 0
        var rowWidth: CGFloat = 0
        let maxWidth = proposal.width ?? .infinity
        
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)

            let candidateWidth = rowWidth == 0 ? size.width : rowWidth + horizontalSpacing + size.width

            if candidateWidth > maxWidth && rowWidth > 0 {
                width = max(width, rowWidth)
                height += rowHeight + verticalSpacing
                rowWidth = size.width
                rowHeight = size.height
            } else {
                rowWidth = candidateWidth
                rowHeight = max(rowHeight, size.height)
            }
        }
        
        width = max(width, rowWidth)
        height += rowHeight
        
        return CGSize(width: width, height: height)
    }

    public func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        var currentX = bounds.minX
        var currentY = bounds.minY
        let maxWidth = bounds.maxX
        var rowHeight: CGFloat = 0
        
        for subview in subviews {
            let size = subview.dimensions(in: .unspecified)
            
            if currentX > bounds.minX && currentX + size.width > maxWidth {

                currentY += rowHeight + verticalSpacing
                currentX = bounds.minX
                rowHeight = 0
            }
            
            subview.place(
                at: CGPoint(x: currentX, y: currentY),
                proposal: .unspecified
            )
            
            currentX += size.width + horizontalSpacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

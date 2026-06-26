import SwiftUI

public struct ListCardView<Content: View>: View {
    @Environment(\.isMultipleColumn) private var isMultipleColumn
    private let index: Int
    private let totalCount: Int
    @ViewBuilder
    private let content: () -> Content
    private let cornerRadius: CGFloat = 32

    public init(
        index: Int,
        totalCount: Int,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.index = index
        self.totalCount = totalCount
        self.content = content
    }

    public var body: some View {
        content()
            .background {
                UnevenRoundedRectangle(
                    cornerRadii: cornerRadii,
                    style: .continuous
                )
                .fill(Color.flareSecondarySystemGroupedBackground)
            }
    }

    private var cornerRadii: RectangleCornerRadii {
        if isMultipleColumn {
            RectangleCornerRadii(
                topLeading: cornerRadius,
                bottomLeading: cornerRadius,
                bottomTrailing: cornerRadius,
                topTrailing: cornerRadius
            )
        } else {
            RectangleCornerRadii(
                topLeading: index == 0 ? cornerRadius : 4,
                bottomLeading: index == totalCount - 1 ? cornerRadius : 4,
                bottomTrailing: index == totalCount - 1 ? cornerRadius : 4,
                topTrailing: index == 0 ? cornerRadius : 4
            )
        }
    }
}

public extension ListCardView {
    init(
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.init(index: 0, totalCount: 1, content: content)
    }
}

import SwiftUI

struct ListCardView<Content: View>: View {
    @Environment(\.isMultipleColumn) private var isMultipleColumn
    let index: Int
    let totalCount: Int
    @ViewBuilder
    let content: () -> Content
    let cornerRadius: CGFloat = 32

    var body: some View {
        content()
            .background {
                UnevenRoundedRectangle(
                    cornerRadii: cornerRadii,
                    style: .continuous
                )
                .fill(Color(.secondarySystemGroupedBackground))
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

extension ListCardView {
    init(
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.init(index: 0, totalCount: 1, content: content)
    }
}

private struct IsMultipleColumn: EnvironmentKey {
    static let defaultValue: Bool = false
}

extension EnvironmentValues {
    var isMultipleColumn: Bool {
        get { self[IsMultipleColumn.self] }
        set { self[IsMultipleColumn.self] = newValue }
    }
}

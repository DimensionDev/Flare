import SwiftUI

struct ListCardView<Content: View>: View {
    let index: Int
    let totalCount: Int
    @ViewBuilder
    let content: () -> Content
    let cornerRadius: CGFloat = 32

    var body: some View {
        content()
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(
                .rect(
                    topLeadingRadius: index == 0 ? cornerRadius : 4,
                    bottomLeadingRadius: index == totalCount - 1 ? cornerRadius : 4,
                    bottomTrailingRadius: index == totalCount - 1 ? cornerRadius : 4,
                    topTrailingRadius: index == 0 ? cornerRadius : 4
                )
            )
    }
}

extension ListCardView {
    init(
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.init(index: 0, totalCount: 1, content: content)
    }
}

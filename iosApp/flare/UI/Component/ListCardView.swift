import SwiftUI

struct ListCardView<Content: View>: View {
    let index: Int
    let totalCount: Int
    @ViewBuilder
    let content: () -> Content
    
    var body: some View {
        content()
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(
                .rect(
                    topLeadingRadius: index == 0 ? 16 : 4,
                    bottomLeadingRadius: index == totalCount - 1 ? 16 : 4,
                    bottomTrailingRadius: index == totalCount - 1 ? 16 : 4,
                    topTrailingRadius: index == 0 ? 16 : 4
                )
            )
    }
}

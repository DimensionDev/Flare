import MarkdownUI
import shared
import SwiftUI

struct TabItemsViewSwiftUI: View {
    @Binding var selection: String
    let items: [FLTabItem]
    @Namespace private var tabNamespace
    @State private var scrollPosition: String?
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            ScrollViewReader { proxy in
                HStack(spacing: 20) {
                    ForEach(items, id: \.key) { item in
                        VStack(spacing: 4) {
                            tabItemView(for: item)
                                .id(item.key)
                                .onTapGesture {
                                    withAnimation(.spring()) {
                                        selection = item.key
                                        scrollPosition = item.key
                                    }
                                }

                            if selection == item.key {
                                Rectangle()
                                    .fill(theme.tintColor)
                                    .frame(height: 2)
                                    .matchedGeometryEffect(id: "activeTab", in: tabNamespace)
                            } else if selection == "" {
                                // first key , show active tab ,home
                                if item.key == items.first?.key {
                                    Rectangle()
                                        .fill(theme.tintColor)
                                        .frame(height: 2)
                                        .matchedGeometryEffect(id: "activeTab", in: tabNamespace)
                                }
                            } else {
                                Rectangle()
                                    .fill(Color.clear)
                                    .frame(height: 2)
                            }
                        }
                    }
                }
                .scrollTargetLayout()
                .onChange(of: selection) { _, newValue in
                    withAnimation(.spring()) {
                        proxy.scrollTo(newValue, anchor: .center)
                    }
                }
            }
        }
        .scrollTargetBehavior(.viewAligned)
        .scrollPosition(id: $scrollPosition)
    }

    @ViewBuilder
    private func tabItemView(for item: FLTabItem) -> some View {
        switch item.metaData.title {
        case let .text(title):
            Markdown(title)
        case let .localized(key):
            let title = NSLocalizedString(key, comment: "")
            Markdown(title)
        }
    }
}

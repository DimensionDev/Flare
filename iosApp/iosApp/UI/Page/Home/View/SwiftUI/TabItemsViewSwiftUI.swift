import MarkdownUI
import shared
import SwiftUI

struct TabItemsViewSwiftUI: View {
    @Binding var selection: String
    let items: [FLTabItem]
    let onScrollToTop: (String) -> Void
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
                                        // 如果点击的是当前已选中的标签，触发返回顶部
                                        if selection == item.key {
                                            FlareLog.debug("TabItemsViewSwiftUI Same tab tapped, triggering scroll to top for: \(item.key)")
                                            onScrollToTop(item.key)
                                        } else {
                                            // 否则正常切换标签
                                            FlareLog.debug("TabItemsViewSwiftUI Switching to tab: \(item.key)")
                                            selection = item.key
                                            scrollPosition = item.key
                                        }
                                    }
                                }


                            let isSelected = selection == item.key ||
                                           (selection.isEmpty && item.key == items.first?.key)

                            if isSelected {
                                Rectangle()
                                    .fill(theme.tintColor)
                                    .frame(height: 2)
                                    .matchedGeometryEffect(id: "activeTab", in: tabNamespace)
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

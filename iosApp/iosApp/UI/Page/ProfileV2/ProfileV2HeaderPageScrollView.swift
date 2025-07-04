
import SwiftUI

struct ProfileV2PageLabel: Identifiable {
    var id: String = UUID().uuidString
    var title: String
    var symbolImage: String
}

@resultBuilder
struct ProfileV2PageLabelBuilder {
    static func buildBlock(_ components: ProfileV2PageLabel...) -> [ProfileV2PageLabel] {
        components.compactMap(\.self)
    }
}

struct ProfileV2HeaderPageScrollView<Header: View, Pages: View>: View {
    private var displaysSymbols: Bool = false
    private var header: Header
    /// Labels(Tab Title or Tab Image)
    private var labels: [ProfileV2PageLabel]
    /// Tab views
    private var pages: Pages
    private var onRefresh: (Int) async -> Void

    init(
        displaysSymbols: Bool,
        @ViewBuilder header: @escaping () -> Header,
        @ProfileV2PageLabelBuilder labels: @escaping () -> [ProfileV2PageLabel],
        @ViewBuilder pages: @escaping () -> Pages,
        onRefresh: @escaping (Int) async -> Void = { _ in }
    ) {
        self.displaysSymbols = displaysSymbols
        self.header = header()
        self.labels = labels()
        self.pages = pages()
        self.onRefresh = onRefresh

        let count = labels().count
        _scrollPositions = .init(initialValue: .init(repeating: .init(), count: count))
        _scrollGeometries = .init(initialValue: .init(repeating: .init(), count: count))
    }

    /// View Properties
    @State private var activeTab: String?
    @State private var headerHeight: CGFloat = 0
    @State private var scrollGeometries: [ScrollGeometry]
    @State private var scrollPositions: [ScrollPosition]
    /// Main Scroll Properties
    @State private var mainScrollDisabled: Bool = false
    @State private var mainScrollPhase: ScrollPhase = .idle
    @State private var mainScrollGeometry: ScrollGeometry = .init()

    var body: some View {
        GeometryReader {
            let size = $0.size

            ScrollView(.horizontal) {
                // 使用HStack允许我们维护对其他滚动视图的引用，使我们能够在必要时更新它们。
                HStack(spacing: 0) {
                    Group(subviews: pages) { collection in
                        // 检查集合和标签是否相互匹配
                        if collection.count != labels.count {
                            Text("Tabviews and labels does not match!")
                                .frame(width: size.width, height: size.height)
                        } else {
                            ForEach(labels) { label in
                                PageScrollView(label: label, size: size, collection: collection)
                            }
                        }
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $activeTab)
            .scrollIndicators(.hidden)
            .scrollDisabled(mainScrollDisabled)
            // 在滚动视图设置动画时禁用交互，以避免意外点击！
            .allowsHitTesting(mainScrollPhase == .idle)
            .onScrollPhaseChange { _, newPhase in
                mainScrollPhase = newPhase
            }
            .onScrollGeometryChange(for: ScrollGeometry.self) {
                $0
            } action: { _, newValue in
                mainScrollGeometry = newValue
            }
            .mask {
                Rectangle()
                    .ignoresSafeArea(.all, edges: .bottom)
            }
            .onAppear {
                guard activeTab == nil else { return }
                activeTab = labels.first?.id
            }
        }
    }

    @ViewBuilder
    func PageScrollView(label: ProfileV2PageLabel, size: CGSize, collection: SubviewsCollection) -> some View {
        let index = labels.firstIndex(where: { $0.id == label.id }) ?? 0

        ScrollView(.vertical) {
            // 使用LazyVStack优化性能
            LazyVStack(spacing: 0, pinnedViews: [.sectionHeaders]) {
                ZStack {
                    if activeTab == label.id {
                        header
                            // 使其具有粘性，以便在以下情况下不会向左或向右移动
                            .visualEffect { content, proxy in
                                content
                                    .offset(x: -proxy.frame(in: .scrollView(axis: .horizontal)).minX)
                            }
                            .onGeometryChange(for: CGFloat.self) { proxy in
                                proxy.size.height
                            } action: { newValue in
                                headerHeight = newValue
                            }
                            .transition(.identity)
                    } else {
                        Rectangle()
                            .foregroundStyle(.clear)
                            .frame(height: headerHeight)
                            .transition(.identity)
                    }
                }
                .simultaneousGesture(horizontalScrollDisableGesture)

                // 使用固定视图将我们的标签栏固定在顶部！
                Section {
                    collection[index]
                        // 让我们使其可滚动到顶部，即使视图没有足够的内容
                        .frame(minHeight: size.height - 40, alignment: .top)
                } header: {
                    ZStack {
                        if activeTab == label.id {
                            CustomTabBar()
                                .visualEffect { content, proxy in
                                    content
                                        .offset(x: -proxy.frame(in: .scrollView(axis: .horizontal)).minX)
                                }
                                .transition(.identity)
                        } else {
                            Rectangle()
                                .foregroundStyle(.clear)
                                .frame(height: 40)
                                .transition(.identity)
                        }
                    }
                    .simultaneousGesture(horizontalScrollDisableGesture)
                }
            }
        }
        .onScrollGeometryChange(for: ScrollGeometry.self, of: { $0 }) { _, newValue in
            scrollGeometries[index] = newValue

            if newValue.offsetY <= 0 {
                resetScrollViews(label)
            }
        }
        .scrollPosition($scrollPositions[index])
        .onScrollPhaseChange { _, newPhase in
            let geometry = scrollGeometries[index]
            let maxOffset = min(geometry.offsetY, headerHeight)
            if newPhase == .idle, maxOffset <= headerHeight {
                updateOtherScrollViews(label, to: maxOffset)
            }
            if newPhase == .idle, mainScrollDisabled {
                mainScrollDisabled = false
            }
        }
        .zIndex(activeTab == label.id ? 1000 : 0)
        .frame(width: size.width)
        .scrollClipDisabled()
        .refreshable {
            await onRefresh(index)
        }
    }

    // 自定义选项卡栏
    @ViewBuilder
    func CustomTabBar() -> some View {
        let progress = mainScrollGeometry.offsetX / mainScrollGeometry.containerSize.width
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 0) {
                ForEach(labels) { label in
                    Group {
                        if displaysSymbols {
                            Image(systemName: label.symbolImage)
                        } else {
                            Text(label.title)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .foregroundStyle(activeTab == label.id ? Color.primary : .gray)
                    .onTapGesture {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            activeTab = label.id
                        }
                    }
                }
            }

            Capsule()
                .frame(width: 50, height: 4)
                .containerRelativeFrame(.horizontal) { value, _ in
                    value / CGFloat(labels.count)
                }
                .visualEffect { content, proxy in
                    content
                        .offset(x: proxy.size.width * progress)
                }
        }
        .frame(height: 40)
        .background(.background)
    }

    var horizontalScrollDisableGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                mainScrollDisabled = true
            }
            .onEnded { _ in
                mainScrollDisabled = false
            }
    }

    // 将页面滚动视图重置为初始位置
    func resetScrollViews(_ from: ProfileV2PageLabel) {
        for index in labels.indices {
            let label = labels[index]
            if label.id != from.id {
                scrollPositions[index].scrollTo(y: 0)
            }
        }
    }

    // 更新其他滚动视图以与当前滚动视图匹配，直到达到其标题高度
    func updateOtherScrollViews(_ from: ProfileV2PageLabel, to: CGFloat) {
        for index in labels.indices {
            let label = labels[index]
            let offset = scrollGeometries[index].offsetY
            let wantsUpdate = offset < headerHeight || to < headerHeight
            if wantsUpdate, label.id != from.id {
                scrollPositions[index].scrollTo(y: to)
            }
        }
    }
}

// MARK: - ScrollGeometry Extension

private extension ScrollGeometry {
    init() {
        self.init(contentOffset: .zero, contentSize: .zero, contentInsets: .init(.zero), containerSize: .zero)
    }

    var offsetY: CGFloat {
        contentOffset.y + contentInsets.top
    }

    var offsetX: CGFloat {
        contentOffset.x + contentInsets.leading
    }
}

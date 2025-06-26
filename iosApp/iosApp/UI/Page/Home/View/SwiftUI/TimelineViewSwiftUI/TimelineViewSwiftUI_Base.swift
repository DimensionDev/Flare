import os
import shared
import SwiftUI

 
struct TimelineViewSwiftUIBase: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    @State private var presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    @Binding var scrollToTopTrigger: Bool
    let isCurrentTab: Bool
    @Binding var showFloatingButton: Bool
    
    var body: some View {
        // 使用Base版本的实现（从TimelineViewSwiftUI_base.swift复制）
        // Use Base version implementation (copied from TimelineViewSwiftUI_base.swift)
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ScrollToTopView(tabKey: tab.key)
                        .id("top")
                        .background(
                            GeometryReader { geometry in
                                Color.clear
                                    .preference(key: ScrollOffsetPreferenceKey.self,
                                                value: geometry.frame(in: .global).minY)
                            }
                        )
                    
                    if let presenter {
                        ObservePresenter(presenter: presenter) { state in
                            if let timelineState = state as? TimelineState,
                               case let .success(success) = onEnum(of: timelineState.listState)
                            {
                                ForEach(0 ..< success.itemCount, id: \.self) { index in
                                    
                                    if let status = success.peek(index: index) {
                                        let statusID = status.itemKey
                                        StatusItemView(data: status, detailKey: nil)
                                            .padding(.vertical, 8)
                                            .padding(.horizontal, 16)
                                            .id("StatusItemView_\(statusID)")
                                            .onAppear {
                                                if index > success.itemCount - 4 {
                                                    success.get(index: index)
                                                }
                                            }
                                            .background(
                                                GeometryReader { _ in
                                                    Color.clear
                                                        .onAppear {
                                                            if index == 0 {
                                                                scrollPositionID = statusID
                                                            }
                                                        }
                                                }
                                            )
                                    } else {
                                        StatusPlaceHolder()
                                            .onAppear {
                                                success.get(index: index)
                                            }
                                            .padding(.vertical, 8)
                                            .padding(.horizontal, 16)
                                    }
                                    
                                    if index < success.itemCount - 1 {
                                        Divider()
                                            .padding(.horizontal, 16)
                                    }
                                }
                            } else if let timelineState = state as? TimelineState {
                                StatusTimelineComponent(
                                    data: timelineState.listState,
                                    detailKey: nil
                                )
                                .padding(.horizontal, 16)
                            }
                        }
                    }
                }
            }
            .onChange(of: scrollToTopTrigger) { _, _ in
                print("[TimelineView_Base] ScrollToTop trigger changed for tab: \(tab.key)")
                guard isCurrentTab else { return }
                
                withAnimation(.easeInOut(duration: 0.5)) {
                    proxy.scrollTo(ScrollToTopView.Constants.scrollToTop, anchor: .top)
                }
            }
        }
        .scrollPosition(id: $scrollPositionID)
        .refreshable {
            if let presenter,
               let timelineState = presenter.models.value as? TimelineState
            {
                try? await timelineState.refresh()
            }
        }
        .task {
            if presenter == nil {
                presenter = store.getOrCreatePresenter(for: tab)
            }
        }
        .coordinateSpace(name: "scroll")
        .onPreferenceChange(ScrollOffsetPreferenceKey.self) { offset in
            handleScrollOffsetChange(offset)
        }
    }
    
    private func handleScrollOffsetChange(_ offset: CGFloat) {
        guard isCurrentTab else { return }
        handleFloatingButtonVisibility(offset)
    }
    
    private func handleFloatingButtonVisibility(_: CGFloat) {
        if !showFloatingButton {
            showFloatingButton = true
        }
    }
}


import shared
import SwiftUI

struct TimelineViewSwiftUI: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    @State private var presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
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
        // 添加两个手势：菜单手势和标签导航手势
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
    }
}

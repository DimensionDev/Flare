import Foundation
import shared
import SwiftUI

struct StatusDetailScreenV2: View {
    let accountType: AccountType
    let statusKey: MicroBlogKey
    let preloadItem: TimelineItem?

    @State private var timelineViewModel = TimelineViewModel()
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        ScrollViewReader { _ in
            VStack {
                List {
                    EmptyView()
                        .id("timeline-top-v4")
                        .frame(height: 0)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets())

                    switch timelineViewModel.timelineState {
                    case .loading:
                        if let preloadItem {
                            TimelineStatusViewV2(
                                item: preloadItem,
                                timelineViewModel: timelineViewModel
                            )
                            .padding(.horizontal, 14)
                            .listRowBackground(theme.primaryBackgroundColor)
                            .listRowInsets(EdgeInsets())
                            .listRowSeparator(.hidden)
                        } else {
                            ForEach(0 ..< 5, id: \.self) { _ in
                                TimelineStatusViewV2(
                                    item: createSampleTimelineItem(),
                                    timelineViewModel: timelineViewModel
                                )
                                .redacted(reason: .placeholder)
                                .listRowBackground(theme.primaryBackgroundColor)
                                .listRowInsets(EdgeInsets())
                                .listRowSeparator(.hidden)
                                .padding(.horizontal, 14)
                            }
                        }

                    case let .loaded(items, hasMore):
                        TimelineItemsView(
                            items: items,
                            hasMore: hasMore,
                            viewModel: timelineViewModel
                        ).padding(.horizontal, 8)
                        .listRowBackground(theme.primaryBackgroundColor)
                        .listRowInsets(EdgeInsets())

                    case let .error(error):
                        TimelineErrorView(error: error) {
                            Task {
                                await timelineViewModel.handleRefresh()
                            }
                        }
                        .listRowInsets(EdgeInsets())

                    case .empty:
                        TimelineEmptyView()
                            .listRowBackground(theme.primaryBackgroundColor)
                            .listRowInsets(EdgeInsets())
                    }

                    EmptyView()
                        .id("timeline-bottom-v4")
                        .frame(height: 0)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets())
                }
                .listStyle(.plain)
                .refreshable {
                    FlareLog.debug("[StatusDetailScreenV2] 下拉刷新触发")
                    await timelineViewModel.handleRefresh()
                    FlareLog.debug("[StatusDetailScreenV2] 下拉刷新完成")
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            Rectangle()
                .fill(Color.clear)
                .frame(height: 120)
        }
        .task {
            FlareLog.debug("🔧 [StatusDetailScreenV2] 开始初始化 - statusKey: \(statusKey)")
            let presenter = StatusContextPresenter(accountType: accountType, statusKey: statusKey)
            await timelineViewModel.setupDataSource(presenter: presenter)
            FlareLog.debug("✅ [StatusDetailScreenV2] 初始化完成")
        }
        .onAppear {
            FlareLog.debug("👁️ [StatusDetailScreenV2] onAppear - 调用resume()")
            timelineViewModel.resume()
        }
        .onDisappear {
            FlareLog.debug("👁️ [StatusDetailScreenV2] onDisappear - 调用pause()")
            timelineViewModel.pause()
        }
    }
}

import SwiftUI
import shared

struct TimelineScreen: View {
    @ObservedObject var timelineStore: TimelineStore
    
    var body: some View {
        if let presenter = timelineStore.currentPresenter {
            List {
                ObservePresenter(presenter: presenter) { state in
                    StatusTimelineComponent(
                        data: state.listState,
                        detailKey: nil
                    )
                    // .listRowInsets(EdgeInsets())
                    //首页列表背景色
                    .listRowBackground(Colors.Background.swiftUIPrimary)
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .refreshable {
                try? await timelineStore.refresh()
            }
        } else {
            ProgressView() // 显示加载状态
        }
    }
}

struct HomeTimelineScreen: View {
    @Environment(\.openURL) private var openURL
    @StateObject private var timelineStore: TimelineStore
    private let accountType: AccountType
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    
    init(accountType: AccountType) {
        self.accountType = accountType
        self._timelineStore = StateObject(wrappedValue: TimelineStore(accountType: accountType))
    }
    
    var body: some View {
        TimelineScreen(timelineStore: timelineStore)
            .navigationBarTitleDisplayMode(.inline)
            #if !os(iOS)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: {
                        Task {
                            try? await timelineStore.refresh()
                        }
                    }, label: {
                        Image(systemName: "arrow.clockwise.circle")
                    })
                }
            }
            #endif
            .navigationTitle("home_timeline_title")
    }
}

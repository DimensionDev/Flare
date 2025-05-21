import shared
import SwiftUI

// struct ProfileContentView: View {
//    let tabs: [FLTabItem]
//    @Binding var selectedTab: Int
//    let refresh: () async -> Void
//    let accountType: AccountType
//    let userKey: MicroBlogKey?
//    @ObservedObject var tabStore: ProfileTabSettingStore
//
//    var body: some View {
//        if let selectedTab = tabStore.availableTabs.first(where: { $0.key == tabStore.selectedTabKey }) {
//            if selectedTab is FLProfileMediaTabItem {
//                ProfileMediaListScreen(accountType: accountType, userKey: userKey, tabStore: tabStore)
//            } else if let presenter = tabStore.currentPresenter {
//                ProfileTimelineView(
//                    presenter: presenter,
//                    refresh: refresh
//                )
//            } else {
//                ProgressView()
//                    .frame(maxWidth: .infinity, maxHeight: .infinity)
////                    .background(FColors.Background.swiftUIPrimary)
//            }
//        } else {
//            ProgressView()
//                .frame(maxWidth: .infinity, maxHeight: .infinity)
////                .background(FColors.Background.swiftUIPrimary)
//        }
//    }
// }

struct ProfileTimelineView: View {
    let presenter: TimelinePresenter?

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
               if let presenter {
                    ObservePresenter(presenter: presenter) { state in
                        if let timelineState = state as? TimelineState,
                           case let .success(success) = onEnum(of: timelineState.listState)
                        {
                            ForEach(0 ..< success.itemCount, id: \.self) { index in
                                let statusID = "status_\(index)"
                                if let status = success.peek(index: index) {
                                    StatusItemView(data: status, detailKey: nil)
                                        .padding(.vertical, 8)
                                        .padding(.horizontal, 16)
                                        .id(statusID)
                                        .onAppear {
                                            success.get(index: index)
                                        }
                                         
                                } else {
                                    StatusPlaceHolder()
                                        .padding(.vertical, 8)
                                        .padding(.horizontal, 16)
                                        .id(statusID)
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
    }
}

import SwiftUI
import shared

struct ProfileContentView: View {
   let tabs: [FLTabItem]
   @Binding var selectedTab: Int
   let refresh: () async -> Void
   let accountType: AccountType
   let userKey: MicroBlogKey?
   @ObservedObject var tabStore: ProfileTabSettingStore
   
   var body: some View {
       if let selectedTab = tabStore.availableTabs.first(where: { $0.key == tabStore.selectedTabKey }) {
           if selectedTab is FLProfileMediaTabItem {
               ProfileMediaListScreen(accountType: accountType, userKey: userKey, tabStore: tabStore)
           } else if let presenter = tabStore.currentPresenter {
               TimelineView(
                   presenter: presenter,
                   refresh: refresh
               )
           } else {
               ProgressView()
                   .frame(maxWidth: .infinity, maxHeight: .infinity)
                   .background(Colors.Background.swiftUIPrimary)
           }
       } else {
           ProgressView()
               .frame(maxWidth: .infinity, maxHeight: .infinity)
               .background(Colors.Background.swiftUIPrimary)
       }
   }
}

private struct TimelineView: View {
   let presenter: TimelinePresenter?
   let refresh: () async -> Void
   
   var body: some View {
       if let presenter = presenter {
           ObservePresenter(presenter: presenter) { state in
               if let timelineState = state as? TimelineState {
                   List {
                       StatusTimelineComponent(
                           data: timelineState.listState,
                           detailKey: nil
                       )
                       .listRowBackground(Colors.Background.swiftUIPrimary)
                   }
                   .listStyle(.plain)
                   .scrollContentBackground(.hidden)
                   .refreshable {
                       await refresh()
                   }
               }
           }
       } else {
           ProgressView()
               .frame(maxWidth: .infinity, maxHeight: .infinity)
               .background(Colors.Background.swiftUIPrimary)
       }
   }
}

import shared
import SwiftUI

struct TimelineViewSwiftUI: View {
    let tab: FLTabItem
    @ObservedObject var store: AppBarTabSettingStore
    @State private var presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    
     var edgeSwipeGesture: some Gesture {
        DragGesture(minimumDistance: 20)
            .onEnded { gesture in
                  
                // left --- right
                guard gesture.translation.width > 80 else { return }
                
                // start point is left edge
                guard gesture.startLocation.x < 30 else { return }
                
                // vertical constraint
                guard abs(gesture.translation.height) < 80 else { return }
                
                // first tab
                guard tab.key == store.availableAppBarTabsItems.first?.key else { return }
                
                // trigger left menu
                NotificationCenter.default.post(name: NSNotification.Name("flShowNewMenu"), object: nil)
            }
    }
    
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                if let presenter = presenter {
                    ObservePresenter(presenter: presenter) { state in
                        if let timelineState = state as? TimelineState,
                           case .success(let success) = onEnum(of: timelineState.listState) {
                            ForEach(0..<success.itemCount, id: \.self) { index in
                                let statusID = "status_\(index)"
                                if let status = success.peek(index: index) {
                                    StatusItemView(data: status, detailKey: nil)
                                        .padding(.vertical, 8)
                                        .padding(.horizontal, 16)
                                        .id(statusID)
                                        .onAppear {
                                            success.get(index: index)
                                        }
                                        .background(
                                            GeometryReader { geo in
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
 
        .simultaneousGesture(edgeSwipeGesture, including: .gesture)
        .scrollPosition(id: $scrollPositionID)
        .refreshable {
            if let presenter = presenter,
               let timelineState = presenter.models.value as? TimelineState {
           
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

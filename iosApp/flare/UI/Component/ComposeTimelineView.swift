import SwiftUI
import KotlinSharedUI
import Combine

struct ComposeTimelineItemView : UIViewControllerRepresentable {
    let key: String
    let data: TimelineItemPresenterWithLazyListStateState
    let state: ComposeUIStateProxy<TimelineItemPresenterWithLazyListStateState>
    let topPadding: Int
    let onExpand: () -> Void
    let onCollapse: () -> Void
    
    init(key: String, data: TimelineItemPresenterWithLazyListStateState, topPadding: Int, onOpenLink: @escaping (String) -> Void, onExpand: @escaping () -> Void, onCollapse: @escaping () -> Void) {
        self.key = key
        self.data = data
        self.topPadding = topPadding
        self.onExpand = onExpand
        self.onCollapse = onCollapse
        if let state = ComposeUIStateProxyCache.shared.getOrCreate(key: key, factory: {
            .init(initialState: data, onOpenLink: onOpenLink)
        }) as? ComposeUIStateProxy<any TimelineItemPresenterWithLazyListStateState> {
            self.state = state
        } else {
            self.state = ComposeUIStateProxy(initialState: data, onOpenLink: onOpenLink)
        }
    }
    
    func makeUIViewController(context: Context) -> some UIViewController {
        return KotlinSharedUI.TimelineItemController(state: state, topPadding: Int32(topPadding), onExpand: onExpand, onCollapse: onCollapse)
    }
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
        state.update(newState: data)
    }
    
    func dismantleUIViewController(_ uiViewController: UIViewController, coordinator: Coordinator) {
        ComposeUIStateProxyCache.shared.remove(key: key)
    }
}

struct ComposeTimelineView : UIViewControllerRepresentable {
    let key: String
    let data: PagingState<UiTimeline>
    let state: ComposeUIStateProxy<PagingState<UiTimeline>>
    let topPadding: Int
    let onExpand: () -> Void
    let onCollapse: () -> Void
    let detailStatusKey: MicroBlogKey?
    
    init(
        key: String,
        data: PagingState<UiTimeline>,
        detailStatusKey: MicroBlogKey?,
        topPadding: Int,
        onOpenLink: @escaping (String) -> Void,
        onExpand: @escaping () -> Void,
        onCollapse: @escaping () -> Void
    ) {
        self.key = key
        self.data = data
        self.topPadding = topPadding
        self.onExpand = onExpand
        self.onCollapse = onCollapse
        self.detailStatusKey = detailStatusKey
        if let state = ComposeUIStateProxyCache.shared.getOrCreate(key: key, factory: {
            .init(initialState: data, onOpenLink: onOpenLink)
        }) as? ComposeUIStateProxy<PagingState<UiTimeline>> {
            self.state = state
        } else {
            self.state = ComposeUIStateProxy(initialState: data, onOpenLink: onOpenLink)
        }
    }
    
    func makeUIViewController(context: Context) -> some UIViewController {
        return KotlinSharedUI.TimelineController(state: state, detailStatusKey: detailStatusKey, topPadding: Int32(topPadding), onExpand: onExpand, onCollapse: onCollapse)
    }
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
        state.update(newState: data)
    }
    
    func dismantleUIViewController(_ uiViewController: UIViewController, coordinator: Coordinator) {
        ComposeUIStateProxyCache.shared.remove(key: key)
    }
}

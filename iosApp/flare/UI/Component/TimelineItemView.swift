import SwiftUI
import KotlinSharedUI
import Combine

struct TimelineItemView : UIViewControllerRepresentable {
    let key: String
    let data: TimelineItemPresenterState
    let state: ComposeUIStateProxy<TimelineItemPresenterState>
    let topPadding: Int
    let onExpand: () -> Void
    let onCollapse: () -> Void
    
    init(key: String, data: TimelineItemPresenterState, topPadding: Int, onOpenLink: @escaping (String) -> Void, onExpand: @escaping () -> Void, onCollapse: @escaping () -> Void) {
        self.key = key
        self.data = data
        self.topPadding = topPadding
        self.onExpand = onExpand
        self.onCollapse = onCollapse
        self.state = ComposeUIStateProxyCache.shared.getOrCreate(key: key, factory: {
            .init(initialState: data, onOpenLink: onOpenLink)
        }) as! ComposeUIStateProxy<any TimelineItemPresenterState>
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

struct TimelineView : UIViewControllerRepresentable {
    let key: String
    let data: PagingState<UiTimeline>
    let state: ComposeUIStateProxy<PagingState<UiTimeline>>
    let topPadding: Int
    let onExpand: () -> Void
    let onCollapse: () -> Void
    
    init(key: String, data: PagingState<UiTimeline>, topPadding: Int, onOpenLink: @escaping (String) -> Void, onExpand: @escaping () -> Void, onCollapse: @escaping () -> Void) {
        self.key = key
        self.data = data
        self.topPadding = topPadding
        self.onExpand = onExpand
        self.onCollapse = onCollapse
        self.state = ComposeUIStateProxyCache.shared.getOrCreate(key: key, factory: {
            .init(initialState: data, onOpenLink: onOpenLink)
        }) as! ComposeUIStateProxy<PagingState<UiTimeline>>
    }
    
    func makeUIViewController(context: Context) -> some UIViewController {
        return KotlinSharedUI.TimelineController(state: state, topPadding: Int32(topPadding), onExpand: onExpand, onCollapse: onCollapse)
    }
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
        state.update(newState: data)
    }
    
    func dismantleUIViewController(_ uiViewController: UIViewController, coordinator: Coordinator) {
        ComposeUIStateProxyCache.shared.remove(key: key)
    }
}

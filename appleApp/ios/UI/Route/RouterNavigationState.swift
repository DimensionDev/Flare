import Observation

@MainActor
@Observable
final class RouterNavigationState<Route> {
    var backStack: [Route] = []
    var sheet: Route?
    var cover: Route?

    func goBack() {
        if !backStack.isEmpty {
            backStack.removeLast()
        }
    }

    func dismissSheet() {
        sheet = nil
    }

    func dismissCover() {
        cover = nil
    }
}

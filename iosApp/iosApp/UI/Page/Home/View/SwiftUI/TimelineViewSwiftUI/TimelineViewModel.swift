import SwiftUI
import Combine
import shared


@MainActor
@Observable
class TimelineViewModel { 
    private(set) var timelineState: FlareTimelineState = .loading
    private(set) var showErrorAlert = false
    private(set) var currentError: FlareError?
    
 
    private(set) var presenter: TimelinePresenter?  
    private let stateConverter = PagingStateConverter() 
    private var refreshDebounceTimer: Timer?
    private var cancellables = Set<AnyCancellable>()
    
 
    var items: [TimelineItem] {
        if case .loaded(let items, _, _) = timelineState {
            return items
        }
        return []
    }
    
    var hasMore: Bool {
        if case .loaded(_, let hasMore, _) = timelineState {
            return hasMore
        }
        return false
    }
    
    var isRefreshing: Bool {
        if case .loaded(_, _, let isRefreshing) = timelineState {
            return isRefreshing
        }
        return false
    }
    
 
    func setupDataSource(for tab: FLTabItem, using store: AppBarTabSettingStore) async {
        FlareLog.debug("Timeline Setting up data source with cache for tab: \(tab.key)")

        guard let cachedPresenter = store.getOrCreatePresenter(for: tab) else {
            FlareLog.error("Timeline Failed to get cached presenter for tab: \(tab.key)")
            currentError = FlareError.data(.parsing)
            showErrorAlert = true
            return
        }

        if self.presenter === cachedPresenter {
            FlareLog.debug("Timeline Using existing presenter for tab: \(tab.key)")
            return
        }

        FlareLog.debug("Timeline Setting new cached presenter for tab: \(tab.key)")
        self.presenter = cachedPresenter

        
        Task {
            for await state in cachedPresenter.models {
                let flareState = await Task.detached(priority: .userInitiated) { [stateConverter] in
                    return stateConverter.convert(state.listState)
                }.value

                self.timelineState = flareState
                FlareLog.debug("Timeline State updated on main thread: \(flareState)")
            }
        }
    }
    
 
    func handleRefresh() async {
        FlareLog.debug("Timeline Handling refresh")

        guard let presenter = presenter else {
            FlareLog.warning("Timeline No presenter available for refresh")
            return
        }

        do {
            let refreshResult = try await Task.detached(priority: .userInitiated) { [presenter] in
                let timelineState = presenter.models.value
                try await timelineState.refresh()
                return true
            }.value

            if refreshResult {
                FlareLog.debug("Timeline Refresh completed")
            }
        } catch {
            FlareLog.error("Timeline Refresh failed: \(error)")
            let flareError = await Task.detached(priority: .utility) {
                return FlareError.from(error)
            }.value

            currentError = flareError
            showErrorAlert = true
        }
    }
    
 
    func getFirstItemID() -> String? {
        guard let firstID = items.first?.id else {
            FlareLog.debug("Timeline ScrollToTop skipped: no items")
            return nil
        }

        FlareLog.debug("Timeline ScrollToTop available: firstID=\(firstID)")
        return firstID
    }
    
  
    func handleError(_ error: FlareError) {
        currentError = error
        showErrorAlert = true
    }
    
 
    func handleScrollOffsetChange(_ offsetY: CGFloat, showFloatingButton: Binding<Bool>) {
        let shouldShow = offsetY > 50

        if showFloatingButton.wrappedValue != shouldShow {
            showFloatingButton.wrappedValue = shouldShow
        }
    }
}

 

struct TimelineLoadingView: View {
    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
                .scaleEffect(1.2)
            Text("Loading Timeline...")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, minHeight: 200)
    }
}

struct TimelineEmptyView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "tray")
                .font(.largeTitle)
                .foregroundColor(.secondary)

            Text("No Timeline Items")
                .font(.headline)

            Text("There are no items to display in this timeline.")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, minHeight: 200)
        .padding()
    }
}

struct TimelineErrorView: View {
    let error: FlareError
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.largeTitle)
                .foregroundColor(.orange)

            Text("Timeline Error")
                .font(.headline)

            Text(error.localizedDescription)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            Button("Retry", action: onRetry)
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, minHeight: 200)
        .padding()
    }
}

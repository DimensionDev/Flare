import SwiftUI

@MainActor
struct TimelineLoadMoreView: View {
    @Environment(FlareTheme.self) private var theme

    @State private var showRetry = false

    let loadNextPage: () async throws -> Void

    init(loadNextPage: @escaping (() async throws -> Void)) {
        self.loadNextPage = loadNextPage
    }

    var body: some View {
        HStack(spacing: 12) {
            if showRetry {
                retryView
            } else {
                loadingView
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .onAppear {
            FlareLog.debug("[TimelineLoadMoreView] onAppear - LoadMoreView appeared on screen")
        }
        .task {
            FlareLog.debug("[TimelineLoadMoreView] task started executing")
            await executeTask()
        }
        .listRowBackground(Color.clear)
        .listRowInsets(EdgeInsets())
        .listRowSeparator(.hidden)
    }

    private var retryView: some View {
        Button {
            FlareLog.debug("[TimelineLoadMoreView] user clicked retry button")
            Task {
                showRetry = false
                await executeTask()
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "arrow.clockwise")
                Text("Retry")
            }
            .font(.footnote)
            .foregroundColor(.blue)
        }
        .buttonStyle(.bordered)
    }

    private var loadingView: some View {
        HStack(spacing: 8) {
            ProgressView()
            Text("Loading...")
        }
        .font(.footnote)
        .foregroundStyle(.secondary)
    }

    private func executeTask() async {
        showRetry = false

        FlareLog.debug("[TimelineLoadMoreView] starting to execute load more")

        do {
            try await loadNextPage()
            FlareLog.debug("[TimelineLoadMoreView] load more executed successfully")
        } catch {
            showRetry = true
            FlareLog.error("[TimelineLoadMoreView] load more execution failed: \(error)")
        }
    }
}

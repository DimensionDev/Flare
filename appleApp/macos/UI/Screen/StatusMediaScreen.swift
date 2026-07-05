import FlareAppleCore
import KotlinSharedUI
import SwiftUI

struct MacStatusMediaResolveRequest: Identifiable {
    let id = UUID()
    let accountType: AccountType
    let statusKey: MicroBlogKey
    let initialIndex: Int
    let preview: String?
}

struct MacStatusMediaResolver: View {
    let request: MacStatusMediaResolveRequest
    let onResolved: ([any UiMedia], Int, String?, MacMediaShareContext?) -> Void
    let onFinished: () -> Void

    @StateObject private var presenter: KotlinPresenter<StatusState>
    @State private var didFinish = false

    var body: some View {
        Color.clear
            .frame(width: 0, height: 0)
            .onAppear {
                resolveIfNeeded()
            }
            .onChange(of: presenter.state.status) { _, _ in
                resolveIfNeeded()
            }
    }

    private func resolveIfNeeded() {
        guard !didFinish else {
            return
        }

        switch onEnum(of: presenter.state.status) {
        case .success(let success):
            guard let content = success.data.timelineContentPost else {
                finish()
                return
            }
            let medias = Array(content.images)
            guard !medias.isEmpty else {
                finish()
                return
            }
            let index = min(max(request.initialIndex, 0), medias.count - 1)
            let shareContext = MacMediaShareContext(
                statusKey: content.statusKey.description(),
                userHandle: content.user?.handle.canonical
            )
            didFinish = true
            DispatchQueue.main.async {
                onResolved(medias, index, request.preview, shareContext)
                onFinished()
            }
        case .error:
            finish()
        case .loading:
            break
        }
    }

    private func finish() {
        didFinish = true
        DispatchQueue.main.async {
            onFinished()
        }
    }
}

extension MacStatusMediaResolver {
    init(
        request: MacStatusMediaResolveRequest,
        onResolved: @escaping ([any UiMedia], Int, String?, MacMediaShareContext?) -> Void,
        onFinished: @escaping () -> Void
    ) {
        self.request = request
        self.onResolved = onResolved
        self.onFinished = onFinished
        self._presenter = .init(
            wrappedValue: .init(
                presenter: StatusPresenter(
                    accountType: request.accountType,
                    statusKey: request.statusKey
                )
            )
        )
    }
}

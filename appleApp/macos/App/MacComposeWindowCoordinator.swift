import KotlinSharedUI
import SwiftUI

struct MacComposeWindowRequest: Identifiable {
    let id: UUID
    let accountType: AccountType?
    let composeStatus: ComposeStatus?
    let draftGroupId: String?

    init(
        id: UUID = UUID(),
        accountType: AccountType? = nil,
        composeStatus: ComposeStatus? = nil,
        draftGroupId: String? = nil
    ) {
        self.id = id
        self.accountType = accountType
        self.composeStatus = composeStatus
        self.draftGroupId = draftGroupId
    }
}

@MainActor
final class MacComposeWindowCoordinator {
    static let shared = MacComposeWindowCoordinator()

    private var requests: [UUID: MacComposeWindowRequest] = [:]

    private init() {}

    func request(for id: UUID?) -> MacComposeWindowRequest {
        guard let id, let request = requests[id] else {
            return MacComposeWindowRequest()
        }
        return request
    }

    func openNew(openWindow: OpenWindowAction) {
        open(request: MacComposeWindowRequest(), openWindow: openWindow)
    }

    func openDraft(groupId: String, openWindow: OpenWindowAction) {
        open(request: MacComposeWindowRequest(draftGroupId: groupId), openWindow: openWindow)
    }

    func openReply(
        accountType: AccountType,
        statusKey: MicroBlogKey,
        openWindow: OpenWindowAction
    ) {
        open(
            request: MacComposeWindowRequest(
                accountType: accountType,
                composeStatus: ComposeStatus.Reply(statusKey: statusKey)
            ),
            openWindow: openWindow
        )
    }

    func openQuote(
        accountType: AccountType,
        statusKey: MicroBlogKey,
        openWindow: OpenWindowAction
    ) {
        open(
            request: MacComposeWindowRequest(
                accountType: accountType,
                composeStatus: ComposeStatus.Quote(statusKey: statusKey)
            ),
            openWindow: openWindow
        )
    }

    private func open(request: MacComposeWindowRequest, openWindow: OpenWindowAction) {
        requests[request.id] = request
        openWindow(id: MacWindowID.compose, value: request.id)
    }
}

struct MacComposeWindowRoot: View {
    @State private var request: MacComposeWindowRequest

    init(requestID: UUID?) {
        _request = .init(
            initialValue: MacComposeWindowCoordinator.shared.request(for: requestID)
        )
    }

    var body: some View {
        MacComposeScreen(request: request)
            .id(request.id)
    }
}

import KotlinSharedUI
import SwiftUI

final class MacComposePrefill: Hashable {
    let id = UUID()
    let text: String
    let cursorPosition: Int
    let imageData: Data
    let fileName: String

    init(
        text: String,
        cursorPosition: Int,
        imageData: Data,
        fileName: String
    ) {
        self.text = text
        self.cursorPosition = cursorPosition
        self.imageData = imageData
        self.fileName = fileName
    }

    static func == (lhs: MacComposePrefill, rhs: MacComposePrefill) -> Bool {
        lhs.id == rhs.id
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

struct MacComposeWindowRequest: Identifiable {
    let id: UUID
    let accountType: AccountType?
    let composeStatus: ComposeStatus?
    let draftGroupId: String?
    let prefill: MacComposePrefill?

    init(
        id: UUID = UUID(),
        accountType: AccountType? = nil,
        composeStatus: ComposeStatus? = nil,
        draftGroupId: String? = nil,
        prefill: MacComposePrefill? = nil
    ) {
        self.id = id
        self.accountType = accountType
        self.composeStatus = composeStatus
        self.draftGroupId = draftGroupId
        self.prefill = prefill
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

    func removeRequest(for id: UUID?) {
        guard let id else { return }
        requests.removeValue(forKey: id)
    }

    func openNew(openWindow: OpenWindowAction) {
        open(request: MacComposeWindowRequest(), openWindow: openWindow)
    }

    func openDraft(groupId: String, openWindow: OpenWindowAction) {
        open(request: MacComposeWindowRequest(draftGroupId: groupId), openWindow: openWindow)
    }

    func openCrossPost(prefill: MacComposePrefill, openWindow: OpenWindowAction) {
        open(
            request: MacComposeWindowRequest(prefill: prefill),
            openWindow: openWindow
        )
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

    func openVVOReplyComment(
        accountType: AccountType,
        statusKey: MicroBlogKey,
        rootId: String,
        openWindow: OpenWindowAction
    ) {
        open(
            request: MacComposeWindowRequest(
                accountType: accountType,
                composeStatus: ComposeStatus.VVOComment(
                    statusKey: statusKey,
                    rootId: rootId
                )
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
    private let requestID: UUID?
    @State private var request: MacComposeWindowRequest

    init(requestID: UUID?) {
        self.requestID = requestID
        _request = .init(
            initialValue: MacComposeWindowCoordinator.shared.request(for: requestID)
        )
    }

    var body: some View {
        MacComposeScreen(request: request)
            .id(request.id)
            .onAppear {
                MacComposeWindowCoordinator.shared.removeRequest(for: requestID)
            }
    }
}

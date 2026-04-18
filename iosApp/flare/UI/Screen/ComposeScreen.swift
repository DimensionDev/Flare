import SwiftUI
import KotlinSharedUI
import PhotosUI
import SwiftUIIntrospect
import SwiftUIBackports
import FlareUI

struct ComposeScreen: View {
    @Environment(\.dismiss) var dismiss
    let accountType: AccountType?
    let composeStatus: ComposeStatus?
    let draftGroupId: String?
    @StateObject private var presenter: KotlinPresenter<ComposeState>
    @State private var showDraftSheet = false

    var body: some View {
        ComposeContent(composeStatus: composeStatus, state: presenter.state, dismiss: { dismiss() }) { item in
            StatusView(data: item, isQuote: true, showMedia: false, forceHideActions: true)
                .padding()
                .clipShape(.rect(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(.separator), lineWidth: 1)
                )
        }
        .sheet(isPresented: $showDraftSheet) {
            draftSheet
        }
        .toolbar {
            if presenter.state.showDraft {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showDraftSheet = true
                    } label: {
                        Text("Drafts")
                    }
                }
            }
        }
    }

    private var draftSheet: some View {
        NavigationStack {
            DraftBoxScreen { groupId in
                presenter.state.loadDraft(groupId: groupId)
                showDraftSheet = false
            }
        }
        .presentationDetents([.medium, .large])
    }
}

extension ComposeScreen {
    init(accountType: AccountType?, composeStatus: ComposeStatus? = nil, draftGroupId: String? = nil) {
        self.accountType = accountType
        self.composeStatus = composeStatus
        self.draftGroupId = draftGroupId
        self._presenter = .init(wrappedValue: .init(presenter: ComposePresenter(accountType: accountType, status: composeStatus, draftGroupId: draftGroupId)))
    }
}

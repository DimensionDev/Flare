import SwiftUI
import KotlinSharedUI

struct StatusAddReactionSheet: View {
    let accountType: AccountType
    @Environment(\.dismiss) private var dismiss
    @StateObject private var presenter: KotlinPresenter<AddReactionState>
    var body: some View {
        StateView(state: presenter.state.emojis) { emojiData in
            EmojiPopup(accountType: accountType, data: emojiData) { emoji in
                presenter.state.select(emoji: emoji)
                dismiss()
            }
        } loadingContent: {
            ProgressView()
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(
                    role: .cancel
                ) {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel")
                    } icon: {
                        Image("fa-xmark")
                    }
                }
            }
        }
    }
}

extension StatusAddReactionSheet {
    init(
        accountType: AccountType,
        statusKey: MicroBlogKey,
    ) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: AddReactionPresenter(accountType: accountType, statusKey: statusKey)))
    }
}

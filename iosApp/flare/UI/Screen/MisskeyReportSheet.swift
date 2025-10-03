import SwiftUI
import KotlinSharedUI

struct MisskeyReportSheet: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var presenter: KotlinPresenter<MisskeyReportState>
    @State private var reason: String = ""
    
    var body: some View {
        List {
            Section {
                TextField(text: $reason) {
                    Text("misskey_report_reason_placeholder")
                }
            } header: {
                Text("misskey_report_reason")
            }
        }
        .navigationTitle("misskey_report_title")
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
            ToolbarItem(placement: .confirmationAction) {
                Button(
//                    role: .confirm
                ) {
                    presenter.state.report(comment: reason)
                    dismiss()
                } label: {
                    Label {
                        Text("Done")
                    } icon: {
                        Image("fa-check")
                    }
                }
                .disabled(reason.isEmpty)
            }
        }
    }
}

extension MisskeyReportSheet {
    init(
        accountType: AccountType,
        userKey: MicroBlogKey,
        statusKey: MicroBlogKey?,
    ) {
        self._presenter = .init(wrappedValue: .init(presenter: MisskeyReportPresenter(accountType: accountType, userKey: userKey, statusKey: statusKey)))
    }
}

import SwiftUI
import KotlinSharedUI
import FlareAppleCore

public struct MisskeyReportSheet: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var presenter: KotlinPresenter<MisskeyReportState>
    @State private var reason: String = ""
    
    public var body: some View {
        List {
            Section {
                TextField(text: $reason) {
                    Text("misskey_report_reason_placeholder", bundle: FlareAppleUILocalization.bundle)
                }
            } header: {
                Text("misskey_report_reason", bundle: FlareAppleUILocalization.bundle)
            }
        }
        .navigationTitle(Text("misskey_report_title", bundle: FlareAppleUILocalization.bundle))
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(
                    role: .cancel
                ) {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel", bundle: FlareAppleUILocalization.bundle)
                    } icon: {
                        Image(fontAwesome: .xmark)
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
                        Text("Done", bundle: FlareAppleUILocalization.bundle)
                    } icon: {
                        Image(fontAwesome: .check)
                    }
                }
                .disabled(reason.isEmpty)
            }
        }
    }
}

public extension MisskeyReportSheet {
    init(
        accountType: AccountType,
        userKey: MicroBlogKey,
        statusKey: MicroBlogKey?,
    ) {
        self._presenter = .init(wrappedValue: .init(presenter: MisskeyReportPresenter(accountType: accountType, userKey: userKey, statusKey: statusKey)))
    }
}

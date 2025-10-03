import SwiftUI
import KotlinSharedUI

struct BlueskyReportSheet: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var presenter: KotlinPresenter<BlueskyReportStatusState>
    @State private var selecedtReason: BlueskyReportStatusStateReportReason? = nil
    
    var body: some View {
        List {
            Picker("bluesky_report_reason", selection: $selecedtReason) {
                ForEach(BlueskyReportStatusStateReportReason.allCases, id: \.self) { reason in
                    VStack {
                        Text(reason.stringKey)
                        Text(reason.descriptionKey)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .tag(reason)
                }
            }
            .pickerStyle(.inline)
        }
        .navigationTitle("bluesky_report")
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
                StateView(state: presenter.state.status) { timeline in
                    Button(
//                        role: .confirm
                    ) {
                        if let reason = selecedtReason {
                            presenter.state.selectReason(value: reason)
                            presenter.state.report(value: reason, status: timeline)
                            dismiss()
                        }
                    } label: {
                        Label {
                            Text("Done")
                        } icon: {
                            Image("fa-check")
                        }
                    }
                    .disabled(selecedtReason == nil)
                }
            }
        }
    }
}


extension BlueskyReportSheet {
    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self._presenter = .init(wrappedValue: .init(presenter: BlueskyReportStatusPresenter(accountType: accountType, statusKey: statusKey)))
    }
}

extension BlueskyReportStatusStateReportReason {
    var stringKey: LocalizedStringResource {
        switch self {
        case .spam:
            return "bluesky_report_reason_spam"
        case .violation:
            return "bluesky_report_reason_violation"
        case .misleading:
            return "bluesky_report_reason_misleading"
        case .sexual:
            return "bluesky_report_reason_sexual"
        case .rude:
            return "bluesky_report_reason_rude"
        case .other:
            return "bluesky_report_reason_other"
        }
    }
    
    var descriptionKey: LocalizedStringResource {
        switch self {
        case .spam:
            return "bluesky_report_reason_spam_description"
        case .violation:
            return "bluesky_report_reason_violation_description"
        case .misleading:
            return "bluesky_report_reason_misleading_description"
        case .sexual:
            return "bluesky_report_reason_sexual_description"
        case .rude:
            return "bluesky_report_reason_rude_description"
        case .other:
            return "bluesky_report_reason_other_description"
        }
    }
    
}

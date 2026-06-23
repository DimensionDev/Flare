import SwiftUI
import KotlinSharedUI
import FlareAppleCore

public struct BlueskyReportSheet: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var presenter: KotlinPresenter<BlueskyReportStatusState>
    @State private var selecedtReason: BlueskyReportStatusStateReportReason? = nil
    
    public var body: some View {
        List {
            Picker(selection: $selecedtReason) {
                ForEach(BlueskyReportStatusStateReportReason.allCases, id: \.self) { reason in
                    VStack {
                        Text(FlareAppleUILocalization.string(reason.stringKey))
                        Text(FlareAppleUILocalization.string(reason.descriptionKey))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .tag(reason)
                }
            } label: {
                Text("bluesky_report_reason", bundle: FlareAppleUILocalization.bundle)
            }
            .pickerStyle(.inline)
        }
        .navigationTitle(Text("bluesky_report", bundle: FlareAppleUILocalization.bundle))
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
                            Text("Done", bundle: FlareAppleUILocalization.bundle)
                        } icon: {
                            Image(fontAwesome: .check)
                        }
                    }
                    .disabled(selecedtReason == nil)
                }
            }
        }
    }
}


public extension BlueskyReportSheet {
    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self._presenter = .init(wrappedValue: .init(presenter: BlueskyReportStatusPresenter(accountType: accountType, statusKey: statusKey)))
    }
}

extension BlueskyReportStatusStateReportReason {
    var stringKey: String {
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

    var descriptionKey: String {
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

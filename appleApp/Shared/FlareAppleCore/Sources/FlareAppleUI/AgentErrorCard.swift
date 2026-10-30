import FlareAppleCore
import KotlinSharedUI
import SwiftUI

struct AgentErrorCard: View {
    let failure: AgentFailure
    let onRetry: (() -> Void)?
    var historyError = false

    @State private var detailsVisible = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label {
                Text("agent_error_title", bundle: .main)
                    .font(.headline)
            } icon: {
                Image(fontAwesome: .circleExclamation)
                    .foregroundStyle(.red)
            }
            ScrollView {
                Text(verbatim: displayMessage)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .textSelection(.enabled)
            }
            .frame(maxHeight: 120)
            .fixedSize(horizontal: false, vertical: true)
            if let onRetry {
                Button(action: onRetry) {
                    Text("agent_error_retry", bundle: .main)
                }
                .buttonStyle(.borderedProminent)
            }
            if !failure.message.isEmpty && (historyError || failure.kind != .unknown) {
                DisclosureGroup(isExpanded: $detailsVisible) {
                    ScrollView {
                        Text(verbatim: failure.message)
                            .font(.caption)
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(maxHeight: 120)
                    .fixedSize(horizontal: false, vertical: true)
                } label: {
                    Text("agent_error_details", bundle: .main)
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
    }

    private var displayMessage: String {
        if historyError { return String(localized: "agent_error_history", bundle: .main) }
        switch failure.kind {
        case .network: return String(localized: "agent_error_network", bundle: .main)
        case .rateLimited: return String(localized: "agent_error_rate_limited", bundle: .main)
        case .service: return String(localized: "agent_error_service", bundle: .main)
        case .configuration: return String(localized: "agent_error_configuration", bundle: .main)
        case .authentication: return String(localized: "agent_error_authentication", bundle: .main)
        case .accessDenied: return String(localized: "agent_error_access_denied", bundle: .main)
        case .quota: return String(localized: "agent_error_quota", bundle: .main)
        case .interrupted: return String(localized: "agent_error_interrupted", bundle: .main)
        default: return failure.message.isEmpty ? String(localized: "agent_error_unknown", bundle: .main) : failure.message
        }
    }
}

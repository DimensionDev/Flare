import FlareAppleCore
import Foundation
import KotlinSharedUI
import SwiftUI

struct AgentChatHistoryScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AgentChatHistoryPresenter())

    var body: some View {
        Group {
            if presenter.state.rooms.isEmpty {
                ContentUnavailableView {
                    Label {
                        Text("agent_history_empty")
                    } icon: {
                        Image(fontAwesome: .robot)
                    }
                }
            } else {
                List {
                    ForEach(presenter.state.rooms, id: \.id) { room in
                        NavigationLink(value: Route.agentChat(room.id, nil)) {
                            AgentChatHistoryRow(room: room)
                        }
                    }
                }
            }
        }
        .navigationTitle("agent_history_title")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                NavigationLink(value: Route.agentChat(Route.newGenericChatConversationId(), nil)) {
                    Image(fontAwesome: .plus)
                }
                .accessibilityLabel(Text("agent_chat_title"))
            }
        }
    }
}

private struct AgentChatHistoryRow: View {
    let room: AgentChatRoom

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(verbatim: room.title)
                .font(.headline)
                .lineLimit(2)

            HStack(spacing: 6) {
                if room.isRunning {
                    ProgressView()
                        .controlSize(.small)
                }

                TimelineView(.periodic(from: .now, by: 60)) { context in
                    Text(relativeUpdatedAtText(now: context.date))
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }

    private var updatedAtDate: Date {
        Date(timeIntervalSince1970: TimeInterval(room.updatedAt) / 1000)
    }

    private func relativeUpdatedAtText(now: Date) -> String {
        let elapsedSeconds = max(60, Int(now.timeIntervalSince(updatedAtDate)))
        let formatter = DateComponentsFormatter()
        formatter.allowedUnits = Self.allowedUnits(for: elapsedSeconds)
        formatter.maximumUnitCount = 1
        formatter.unitsStyle = .abbreviated
        return formatter.string(from: TimeInterval(elapsedSeconds)) ?? ""
    }

    private static func allowedUnits(for elapsedSeconds: Int) -> NSCalendar.Unit {
        switch elapsedSeconds {
        case ..<3_600:
            return .minute
        case ..<86_400:
            return .hour
        case ..<604_800:
            return .day
        case ..<2_592_000:
            return .weekOfMonth
        case ..<31_536_000:
            return .month
        default:
            return .year
        }
    }
}

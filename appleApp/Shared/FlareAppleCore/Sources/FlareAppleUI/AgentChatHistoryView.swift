import FlareAppleCore
import Foundation
import KotlinSharedUI
import SwiftUI

public struct AgentChatHistoryList<RowContent: View>: View {
    private let rooms: [AgentChatRoom]
    private let selectedConversationId: Binding<String?>?
    private let rowContent: (AgentChatRoom) -> RowContent

    public init(
        rooms: [AgentChatRoom],
        selectedConversationId: Binding<String?>? = nil,
        @ViewBuilder rowContent: @escaping (AgentChatRoom) -> RowContent
    ) {
        self.rooms = rooms
        self.selectedConversationId = selectedConversationId
        self.rowContent = rowContent
    }

    public var body: some View {
        list
            .overlay {
                if rooms.isEmpty {
                    AgentChatHistoryEmptyView()
                }
            }
    }

    @ViewBuilder
    private var list: some View {
        if let selectedConversationId {
            List(selection: selectedConversationId) {
                rows
            }
        } else {
            List {
                rows
            }
        }
    }

    @ViewBuilder
    private var rows: some View {
        ForEach(rooms, id: \.id) { room in
            rowContent(room)
        }
    }
}

public struct AgentChatHistoryRow: View {
    private let room: AgentChatRoom

    public init(room: AgentChatRoom) {
        self.room = room
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(verbatim: room.title)
                .font(.headline)
                .lineLimit(2)

            HStack(spacing: 6) {
                if room.isRunning {
                    runningIndicator
                }

                SwiftUI.TimelineView(.periodic(from: Date.now, by: 60)) { context in
                    Text(relativeUpdatedAtText(now: context.date))
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var runningIndicator: some View {
        #if os(macOS)
        ProgressView()
            .progressViewStyle(.circular)
            .scaleEffect(0.5)
        #else
        ProgressView()
            .controlSize(.small)
        #endif
    }

    private var updatedAtDate: Date {
        Date(timeIntervalSince1970: TimeInterval(room.updatedAt) / 1_000)
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

public struct AgentChatHistoryEmptyView: View {
    public init() {}

    public var body: some View {
        ContentUnavailableView {
            Label {
                Text("agent_history_empty", bundle: .main)
            } icon: {
                Image(fontAwesome: .robot)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

public struct AgentChatHistoryDetailPlaceholder: View {
    private let onNewChat: () -> Void

    public init(onNewChat: @escaping () -> Void) {
        self.onNewChat = onNewChat
    }

    public var body: some View {
        ContentUnavailableView {
            Label {
                Text("agent_history_title", bundle: .main)
            } icon: {
                Image(fontAwesome: .robot)
            }
        } description: {
            Text("macos_placeholder_agent_history", bundle: .main)
        } actions: {
            Button(action: onNewChat) {
                Label {
                    Text("agent_chat_title", bundle: .main)
                } icon: {
                    Image(fontAwesome: .plus)
                }
            }
            .buttonStyle(.borderedProminent)
        }
    }
}

public extension String {
    var isPendingAgentConversationId: Bool {
        hasPrefix("generic-chat:") ||
            hasPrefix("local-history:") ||
            hasPrefix("status-insight:") ||
            hasPrefix("profile-insight:")
    }
}

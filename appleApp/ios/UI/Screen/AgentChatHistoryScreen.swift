import FlareAppleCore
import FlareAppleUI
import Foundation
import KotlinSharedUI
import SwiftUI

struct AgentChatHistoryScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AgentChatHistoryPresenter())

    var body: some View {
        AgentChatHistoryList(rooms: rooms) { room in
            NavigationLink(value: Route.agentChat(room.id, nil)) {
                AgentChatHistoryRow(room: room)
            }
            .swipeActions(edge: .trailing) {
                deleteButton(for: room)
            }
            .contextMenu {
                deleteButton(for: room)
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

    private var rooms: [AgentChatRoom] {
        Array(presenter.state.rooms)
    }

    private func delete(_ room: AgentChatRoom) {
        presenter.state.delete(conversationId: room.id)
    }

    private func deleteButton(for room: AgentChatRoom) -> some View {
        Button(role: .destructive) {
            delete(room)
        } label: {
            Label {
                Text("delete")
            } icon: {
                Image(fontAwesome: .trash)
            }
        }
    }
}

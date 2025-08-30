import os.log
import shared
import SwiftUI

struct ProfileMoreButtonView: View {
    let presenter: ProfilePresenter?
    let userKey: MicroBlogKey
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        Menu {
            if let presenter {
                ObservePresenter<ProfileState, ProfilePresenter, AnyView>(presenter: presenter) { state in
                    AnyView(Group {
                        profileMenuContent(state: state)
                    })
                }
            } else {
                Button(role: .destructive, action: {
                    handleBasicReport()
                }) {
                    Label("Report", systemImage: "exclamationmark.circle")
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .foregroundColor(theme.tintColor)
        }
        .menuStyle(BorderlessButtonMenuStyle())
    }

    @ViewBuilder
    private func profileMenuContent(state: ProfileState) -> some View {
        switch onEnum(of: state.actions) {
        case let .success(actionsData):
            switch onEnum(of: state.relationState) {
            case let .success(relationData):
                let actions = actionsData.data
                let relation = relationData.data as UiRelation
                ForEach(0 ..< actions.size, id: \.self) { index in
                    let action = actions.get(index: Int32(index))
                    Button(action: {
                        handleProfileAction(action, relation: relation, state: state)
                    }) {
                        if action is ProfileActionBlock {
                            if action.relationState(relation: relation) {
                                Label("Unblock", systemImage: "person.slash.fill")
                            } else {
                                Label("Block", systemImage: "person.slash")
                            }
                        } else if action is ProfileActionMute {
                            if action.relationState(relation: relation) {
                                Label("Unmute", systemImage: "speaker.wave.3")
                            } else {
                                Label("Mute", systemImage: "speaker.slash")
                            }
                        }
                    }
                }

                if actions.size > 0 {
                    Divider()
                }
            default:
                Button("Loading user info...") {
                    Task {
                        try? await state.refresh()
                    }
                }
                .disabled(true)
            }
        default:
            Button("Loading user info...") {
                Task {
                    try? await state.refresh()
                }
            }
            .disabled(true)
        }

        Button(role: .destructive, action: {
            handleReport(state: state)
        }) {
            Label("Report", systemImage: "exclamationmark.circle")
        }
    }

    private func handleProfileAction(_ action: ProfileAction, relation: UiRelation, state: ProfileState) {
        Task {
            state.onProfileActionClick(
                userKey: userKey,
                relation: relation,
                action: action
            )

            await MainActor.run {
                let actionName = action is ProfileActionBlock ? "Block" : "Mute"
                let isUndo = action.relationState(relation: relation)
                let message = isUndo ? "\(actionName) removed" : "\(actionName) applied"

                ToastView(
                    icon: UIImage(systemName: "checkmark.circle"),
                    message: message
                ).show()
            }
        }
    }

    private func handleReport(state: ProfileState) {
        Task {
            state.report(userKey: userKey)
            await MainActor.run {
                ToastView(
                    icon: UIImage(systemName: "checkmark.circle"),
                    message: "Report submitted successfully"
                ).show()
            }
        }
    }

    private func handleBasicReport() {
        ToastView(
            icon: UIImage(systemName: "info.circle"),
            message: "Report functionality not available"
        ).show()
    }
}

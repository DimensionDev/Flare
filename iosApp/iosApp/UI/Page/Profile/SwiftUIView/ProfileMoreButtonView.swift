import SwiftUI
import shared
import os.log

struct ProfileMoreButtonView: View {
    let presenter: ProfilePresenter?
    let userKey: MicroBlogKey
    @Environment(FlareTheme.self) private var theme
    
    var body: some View {
        if let presenter = presenter {
            ObservePresenter<ProfileState, ProfilePresenter, AnyView>(presenter: presenter) { state in
                let profileState = state

                AnyView(Menu {

                    switch onEnum(of: profileState.actions) {
                    case .success(let actionsData):
                        switch onEnum(of: profileState.relationState) {
                        case .success(let relationData):
                            let actions = actionsData.data
                            let relation = relationData.data as UiRelation
                            ForEach(0 ..< actions.size, id: \.self) { index in
                                let action = actions.get(index: Int32(index))
                            Button(action: {

                                handleProfileAction(action, relation: relation, state: profileState)
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
                            EmptyView()
                        }
                    default:
                        EmptyView()
                    }


                    Button(role: .destructive, action: {
                        handleReport(state: profileState)
                    }) {
                        Label("Report", systemImage: "exclamationmark.circle")
                    }

                    // TODO: Edit List
                    // if let relationState = profileState.relationState.data,
                    //    relationState.following,
                    //    let isListDataSource = profileState.isListDataSource.data,
                    //    isListDataSource {
                    //     Button(action: { handleEditList() }) {
                    //         Label("Edit List", systemImage: "list.bullet")
                    //     }
                    // }
                    
                    // TODO: Send Message
                    // if let canSendMessage = profileState.canSendMessage.data,
                    //    canSendMessage {
                    //     Button(action: { handleSendMessage() }) {
                    //         Label("Send Message", systemImage: "message")
                    //     }
                    // }
                    
                    // TODO: Search User Using Account -
                    // 需要实现多账户跨平台搜索功能
                    
                } label: {
                    // 保持现有的ellipsis.circle图标
                    Image(systemName: "ellipsis.circle")
                        .foregroundColor(theme.tintColor)
                }
                .menuStyle(BorderlessButtonMenuStyle()))
            }
        } else {

            Menu {
                Button(role: .destructive, action: {
                    handleBasicReport()
                }) {
                    Label("Report", systemImage: "exclamationmark.circle")
                }
            } label: {
                Image(systemName: "ellipsis.circle")
                    .foregroundColor(theme.tintColor)
            }
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
    


    // private func handleEditList() {
    //     print("Edit List functionality - to be implemented")
    // }
    

    // private func handleSendMessage() {
    //     print("Send Message functionality - to be implemented")
    // }
    

    // private func handleSearchUserUsingAccount() {
    //     print("Search User Using Account functionality - to be implemented")
    // }
}

import SwiftUI
import shared
import os.log

struct FollowButtonView: View {
    let presenter: ProfilePresenter?
    let userKey: MicroBlogKey
    @Environment(FlareTheme.self) private var theme

     
    var body: some View {
        if let presenter = presenter {
            ObservePresenter(presenter: presenter) { state in
                let profileState = state
                followButtonContent(state: profileState)
            }
        } else {
            
            loadingButton()
        }
    }
    
    @ViewBuilder
    private func followButtonContent(state: ProfileState) -> some View {
        switch onEnum(of: state.relationState) {
        case .loading:
            loadingButton()
        case .error(let error):
            errorButton(error: error)
        case .success(let rel):
            successButton(relation: rel.data as UiRelation, state: state)
        }
    }
    
    @ViewBuilder
    private func loadingButton() -> some View {
 
        Button(action: {}) {
            HStack(spacing: 4) {
                Text("Follow")
                    .font(.caption)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Color.gray.opacity(0.3))
            .clipShape(RoundedRectangle(cornerRadius: 15))
        }
        .disabled(true)
    }
    
    @ViewBuilder
    private func errorButton(error: UiStateError<UiRelation>) -> some View {
 
        Button(action: {
            //
         }) {
            Text("Retry")
                .font(.caption)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.red.opacity(0.2))
                .clipShape(RoundedRectangle(cornerRadius: 15))
        }
    }
    
    @ViewBuilder
    private func successButton(relation: UiRelation, state: ProfileState) -> some View {
        let buttonTitle = getButtonTitle(relation: relation)
 
        Button(action: {
            handleFollowTap(relation: relation, state: state)
        }) {
            Text(buttonTitle)
                .font(.caption)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color(UIColor(theme.tintColor)))
                .foregroundColor(.white)
                .clipShape(RoundedRectangle(cornerRadius: 15))
        }
        .buttonStyle(.borderless)
    }
    
    private func getButtonTitle(relation: UiRelation) -> String {
        if relation.blocking {
            return NSLocalizedString("profile_header_button_blocked", comment: "")
        } else if relation.following {
            return NSLocalizedString("profile_header_button_following", comment: "")
        } else if relation.hasPendingFollowRequestFromYou {
            return NSLocalizedString("profile_header_button_requested", comment: "")
        } else {
            return NSLocalizedString("profile_header_button_follow", comment: "")
        }
    }
    
    private func handleFollowTap(relation: UiRelation, state: ProfileState) {
 
         FlareHapticManager.shared.buttonPress()

         state.follow(userKey: userKey, data: relation)
 
    }
}

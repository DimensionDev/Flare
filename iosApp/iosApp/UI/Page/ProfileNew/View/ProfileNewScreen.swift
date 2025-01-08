import SwiftUI
import shared
import os.log

struct ProfileNewScreen: View {
    let showBackButton: Bool
    
    init(showBackButton: Bool = true) {
        self.showBackButton = showBackButton
    }
    
    var body: some View {
        ProfileNewRefreshViewControllerWrapper(showBackButton: showBackButton)
            .ignoresSafeArea(edges: .top)
    }
}

struct ProfileNewRefreshViewControllerWrapper: UIViewControllerRepresentable {
    let showBackButton: Bool
    
    func makeUIViewController(context: Context) -> ProfileNewRefreshViewController {
        let controller = ProfileNewRefreshViewController()
         return controller
    }
    
    func updateUIViewController(_ uiViewController: ProfileNewRefreshViewController, context: Context) {
     }
}

 

import SwiftUI
import SwiftUIIntrospect

extension View {
    func navigationBackButtonTitle(_ title: String) -> some View {
        introspect(.viewController, on: .iOS(.v17, .v18, .v26, .v27), scope: .ancestor) { controller in
            // History menu text must not add a visible navigation title.
            controller.navigationItem.backButtonTitle = title
        }
    }
}

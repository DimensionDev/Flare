import SwiftUI
import SwiftUIIntrospect
import UIKit

@available(iOS 18.0, *)
struct IPadCompactTabBarModifier: ViewModifier {
    let primaryTabIDs: [String]
    @State private var tabController = WeakTabController()

    func body(content: Content) -> some View {
        content
            .introspect(.tabView, on: .iOS(.v18, .v26, .v27)) { controller in
                guard controller.traitCollection.userInterfaceIdiom == .pad else { return }

                tabController.value = controller
                updateCompactTabs(controller)
            }
            .onChange(of: primaryTabIDs) {
                // SwiftUI may replace the tabs later in the current view update.
                DispatchQueue.main.async {
                    guard let controller = tabController.value else { return }
                    updateCompactTabs(controller)
                }
            }
    }

    private func updateCompactTabs(_ controller: UITabBarController) {
        // UIKit rebuilds the compact tab bar before SwiftUI removes sidebar sections.
        // Exclude those sections in advance to avoid the iOS 27 tab bar rebuild crash.
        let identifiers = controller.tabs.filter {
            !($0 is UITabGroup) && $0.preferredPlacement != .sidebarOnly
        }.map(\.identifier)
        if controller.compactTabIdentifiers != identifiers {
            controller.compactTabIdentifiers = identifiers
        }
    }

    private final class WeakTabController {
        weak var value: UITabBarController?
    }
}

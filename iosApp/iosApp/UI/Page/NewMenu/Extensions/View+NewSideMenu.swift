import SwiftUI

extension View {
    func newSideMenu(
        isOpen: Binding<Bool>,
        menu: some View
    ) -> some View {
        FLNewSideMenu(
            isOpen: isOpen,
            menu: menu,
            content: self
        )
    }

    func newSideMenuStyle(
        configuration: FLNewSideMenuConfiguration = .default
    ) -> some View {
        shadow(
            radius: configuration.shadowRadius
//               opacity: configuration.shadowOpacity
        )
    }
}

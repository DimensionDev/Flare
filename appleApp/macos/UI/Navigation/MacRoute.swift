import KotlinSharedUI
import SwiftUI

enum MacRoute: Hashable, Identifiable {
    case home
    case notification
    case discover

    var id: Int {
        hashValue
    }

    @MainActor
    @ViewBuilder
    func view(
        onNavigate: @escaping (MacRoute) -> Void,
        goBack: @escaping () -> Void
    ) -> some View {
        switch self {
        case .home:
            HomeScreen()
        case .notification:
            PlaceholderPanel(destination: .notifications)
        case .discover:
            PlaceholderPanel(destination: .discover)
        }
    }
}

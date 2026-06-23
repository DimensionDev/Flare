import FlareAppleCore
import Foundation
import KotlinSharedUI

extension HomeTabsPresenterStateHomeTabs {
    var macOSInitialRoute: Route {
        switch self {
        case .home:
            .empty
        case .notifications:
            .notification
        case .discover:
            .discover
        }
    }

    var macOSTitle: String {
        switch self {
        case .home:
            String(localized: "home_tab_home_title", defaultValue: "Home", bundle: .main)
        case .notifications:
            String(localized: "home_tab_notifications_title", defaultValue: "Notifications", bundle: .main)
        case .discover:
            String(localized: "home_tab_discover_title", defaultValue: "Discover", bundle: .main)
        }
    }

    var macOSIcon: FontAwesomeIcon {
        switch self {
        case .home:
            .house
        case .notifications:
            .bell
        case .discover:
            .magnifyingGlass
        }
    }

    var macOSPlaceholder: String {
        switch self {
        case .home:
            String(localized: "macos_placeholder_home", defaultValue: "Timeline content will render here.", bundle: .main)
        case .notifications:
            String(localized: "macos_placeholder_notifications", defaultValue: "Notification content will render here.", bundle: .main)
        case .discover:
            String(localized: "macos_placeholder_discover", defaultValue: "Search and discovery content will render here.", bundle: .main)
        }
    }

    var macOSShowsTimelineSkeleton: Bool {
        switch self {
        case .home, .notifications:
            true
        case .discover:
            false
        }
    }
}

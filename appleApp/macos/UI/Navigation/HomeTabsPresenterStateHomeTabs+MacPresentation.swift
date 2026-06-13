import AppleFontAwesome
import KotlinSharedUI

extension HomeTabsPresenterStateHomeTabs {
    var macOSInitialRoute: Route {
        switch self {
        case .home:
            .home
        case .notifications:
            .notification
        case .discover:
            .discover
        }
    }

    var macOSTitle: String {
        switch self {
        case .home:
            LocalizedStrings.string("home_tab_home_title", fallback: "Home")
        case .notifications:
            LocalizedStrings.string("home_tab_notifications_title", fallback: "Notifications")
        case .discover:
            LocalizedStrings.string("home_tab_discover_title", fallback: "Discover")
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
            LocalizedStrings.string("macos_placeholder_home", fallback: "Timeline content will render here.")
        case .notifications:
            LocalizedStrings.string("macos_placeholder_notifications", fallback: "Notification content will render here.")
        case .discover:
            LocalizedStrings.string("macos_placeholder_discover", fallback: "Search and discovery content will render here.")
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

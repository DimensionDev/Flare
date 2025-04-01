enum FlareHomeTabs: Int, Equatable, Hashable, Identifiable {
    var id: Self { self }
    case timeline = 0
    case notification = 1
    case compose = 2
    case discover = 3
    case profile = 4
//    case menu = 5

    var customizationID: String {
        switch self {
        case .timeline: "home_timeline"
        case .notification: "home_notification"
        case .compose: "home_compose"
        case .discover: "home_discover"
        case .profile: "home_profile"
//        case .menu: "home_menu"
        }
    }
}

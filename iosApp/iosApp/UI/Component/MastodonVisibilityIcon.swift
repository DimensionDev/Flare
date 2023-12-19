import SwiftUI
import shared

struct MastodonVisibilityIcon: View {
    let visibility: UiStatus.Mastodon.MastodonVisibility
    var body: some View {
        switch visibility {
        case .public:
            Image(systemName: "globe")
        case .unlisted:
            Image(systemName: "lock.open")
        case .private:
            Image(systemName: "lock")
        case .direct:
            Image(systemName: "at")
        }
    }
}

struct MisskeyVisibilityIcon: View {
    let visibility: UiStatus.Mastodon.MisskeyVisibility
    var body: some View {
        switch visibility {
        case .public:
            Image(systemName: "globe")
        case .home:
            Image(systemName: "lock.open")
        case .followers:
            Image(systemName: "lock")
        case .specified:
            Image(systemName: "at")
        }
    }
}

#Preview {
    VStack {
        MastodonVisibilityIcon(visibility: UiStatus.Mastodon.MastodonVisibility.public)
        MastodonVisibilityIcon(visibility: UiStatus.Mastodon.MastodonVisibility.unlisted)
        MastodonVisibilityIcon(visibility: UiStatus.Mastodon.MastodonVisibility.private)
        MastodonVisibilityIcon(visibility: UiStatus.Mastodon.MastodonVisibility.direct)
    }
}

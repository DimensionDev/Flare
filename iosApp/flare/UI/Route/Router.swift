import SwiftUI
import KotlinSharedUI
import LazyPager
import Combine

struct Router<Root: View>: View {
    @Environment(\.openURL) private var openURL
    @ViewBuilder let root: (@escaping (Route) -> Void) -> Root
    @State private var backStack: [Route] = []
    @State private var sheet: Route? = nil
    @State private var cover: Route? = nil
    @State private var deleteStatusData: (AccountType, MicroBlogKey)? = nil
    @State private var showDeleteStatusAlert = false
    @State private var showMastodonReportStatusAlert = false
    @State private var mastodonReportStatusData: (AccountType, MicroBlogKey, MicroBlogKey?)? = nil
    @StateObject private var deepLinkPresenter: KotlinPresenter<DeepLinkPresenterState>
    @StateObject private var deepLinkHandler = DeepLinkHandler()
    
    init(@ViewBuilder root: @escaping (@escaping (Route) -> Void) -> Root) {
        self.root = root
        let handler = DeepLinkHandler()
        self._deepLinkHandler = .init(wrappedValue: handler)
        self._deepLinkPresenter = .init(wrappedValue: .init(presenter: DeepLinkPresenter(onRoute: { [weak handler] deeplinkRoute in
            if let route = Route.fromDeepLinkRoute(deeplinkRoute: deeplinkRoute){
                handler?.onRoute?(route)
            }
        }, onLink: { [weak handler] link in
            handler?.onLink?(link)
        })))
    }
    
    var body: some View {
        NavigationStack(path: $backStack) {
            root({ route in
                navigate(route: route)
            })
            .navigationDestination(for: Route.self) { route in
                route.view(
                    onNavigate: { route in navigate(route: route) },
                    clearToHome: { backStack.removeAll() }
                )
            }
        }
        .sheet(item: $sheet) { route in
            NavigationStack {
                route.view(
                    onNavigate: { route in navigate(route: route) },
                    clearToHome: { backStack.removeAll() }
                )
            }
        }
        .fullScreenCover(item: $cover) { route in
            NavigationStack {
                route.view(
                    onNavigate: { route in navigate(route: route) },
                    clearToHome: { backStack.removeAll() }
                )
            }
            .background(ClearFullScreenBackground())
            .colorScheme(.dark)
        }
        .alert("delete_alert_title", isPresented: $showDeleteStatusAlert, presenting: deleteStatusData) { data in
            Button("Cancel", role: .cancel) {}
            Button("delete", role: .destructive) {
                DeleteStatusPresenter(accountType: data.0, statusKey: data.1).models.value.delete()
            }
        } message: { data in
            Text("delete_status_alert_message")
        }
        .alert("mastodon_report_status_alert_title", isPresented: $showMastodonReportStatusAlert, presenting: mastodonReportStatusData) { data in
            Button("Cancel", role: .cancel) {}
            Button("report", role: .destructive) {
                MastodonReportPresenter(accountType: data.0, userKey: data.1, statusKey: data.2).models.value.report()
            }
        } message: { data in
            Text("mastodon_report_status_alert_message")
        }
        .environment(\.openURL, OpenURLAction { url in
            deepLinkPresenter.state.handle(url: url.absoluteString)
            return .handled
        })
        .onAppear {
            deepLinkHandler.onRoute = { route in
                navigate(route: route)
            }
            deepLinkHandler.onLink = { link in
                if let url = URL(string: link) {
                    openURL(url)
                }
            }
        }
    }

    func navigate(route: Route) {
        if case .statusDeleteConfirm(let accountType, let statusKey) = route {
            deleteStatusData = (accountType, statusKey)
            showDeleteStatusAlert = true
        } else if case .statusMastodonReport(let accountType, let userKey, let statusKey) = route {
            mastodonReportStatusData = (accountType, userKey, statusKey)
            showMastodonReportStatusAlert = true
        } else if isSheetRoute(route: route) {
            sheet = route
        } else if isFullScreenCover(route: route) {
            cover = route
        } else if backStack.last != route {
            backStack.append(route)
            sheet = nil
            cover = nil
        }
    }
    
    func isSheetRoute(route: Route) -> Bool {
        switch route {
        case .deepLinkAccountPicker,
                .composeNew,
                .composeQuote,
                .composeReply,
                .composeVVOReplyComment,
                .tabSettings,
                .statusBlueskyReport,
                .statusMisskeyReport,
                .statusAddReaction:
            return true
        default:
            return false
        }
    }
    
    func isFullScreenCover(route: Route) -> Bool {
        switch route {
        case .mediaStatusMedia, .mediaImage:
            return true
        default:
            return false
        }
    }
}

class DeepLinkHandler : ObservableObject {
    var onRoute: ((Route) -> Void)?
    var onLink: ((String) -> Void)?
}

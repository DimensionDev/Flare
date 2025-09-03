import os.log
import shared
import SwiftUI

struct FlareRootView: View {
    @State var menuState = FlareMenuState()
    @StateObject private var router = FlareRouter.shared
    @StateObject private var composeManager = ComposeManager.shared
    @State private var timelineState = TimelineExtState()
    @State private var presenter = ActiveAccountPresenter()
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        ObservePresenter<UserState, ActiveAccountPresenter, AnyView>(presenter: presenter) { userState in
            AnyView(
                NavigationView {
                    let accountType: AccountType? = switch onEnum(of: userState.user) {
                    case let .success(data): AccountTypeSpecific(accountKey: data.data.key)
                    case .loading:
                        #if os(macOS)
                            AccountTypeGuest()
                        #else
                            nil as AccountType?
                        #endif
                    case .error: AccountTypeGuest()
                    }

                    if let accountType {
                        let _ = os_log("[FlareRootView] Rendering with router: %{public}@",
                                       log: .default, type: .debug,
                                       String(describing: ObjectIdentifier(router)))

                        HomeTabViewContentV2(accountType: accountType)
                            .environment(theme)
                            .applyTheme(theme)
                            .environment(menuState)
                            .environment(router)
                            .environment(timelineState)
                            .sheet(isPresented: $router.isSheetPresented) {
                                if let destination = router.activeDestination {
                                    FlareDestinationView(
                                        destination: destination,
                                        router: router
                                    ).environment(theme)
                                        .applyTheme(theme)
                                        .environment(menuState)
                                }
                            }
                            .fullScreenCover(isPresented: $router.isFullScreenPresented) {
                                if let destination = router.activeDestination {
                                    FlareDestinationView(destination: destination, router: router)
                                        .modifier(SwipeToDismissModifier(onDismiss: {
                                            router.dismissFullScreenCover()
                                        }))
                                        .environment(theme)
                                        .applyTheme(theme)
                                        .environment(\.appSettings, appSettings)
                                        .environment(menuState)
                                }
                            }
                            .alert(isPresented: $router.isDialogPresented) {
                                Alert(
                                    title: Text("Confirmation"),
                                    message: Text("Are you sure?"),
                                    primaryButton: .destructive(Text("OK")) { /* Handle OK */ },
                                    secondaryButton: .cancel()
                                )
                            }
                            .sheet(isPresented: $composeManager.showCompose) {
                                if let composeAccountType = composeManager.composeAccountType {
                                    NavigationView {
                                        ComposeScreen(
                                            onBack: { composeManager.dismiss() },
                                            accountType: composeAccountType,
                                            status: convertToSharedComposeStatus(composeManager.composeStatus)
                                        )
                                        .environment(theme)
                                        .applyTheme(theme)
                                        .environment(router)
                                        .environment(menuState)
                                        .environment(\.appSettings, appSettings)
                                    }
                                }
                            }
                    } else {
                        ProgressView()
                    }
                }
                .environment(theme)
                .applyTheme(theme)
                .navigationViewStyle(StackNavigationViewStyle())
                .onAppear {
                    setupInitialState()
                    router.menuState = menuState
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            )
        }
    }

    private func setupInitialState() {}

    private func convertToSharedComposeStatus(_ status: FlareComposeStatus?) -> shared.ComposeStatus? {
        guard let status else { return nil }

        switch status {
        case let .reply(statusKey):
            return shared.ComposeStatusReply(statusKey: statusKey)
        case let .quote(statusKey):
            return shared.ComposeStatusQuote(statusKey: statusKey)
        case let .vvoComment(statusKey, rootId):
            return shared.ComposeStatusVVOComment(statusKey: statusKey, rootId: rootId)
        }
    }
}

import os.log
import shared
import SwiftUI

struct FlareRootView: View {
    @StateObject var appState = FlareAppState()
    @StateObject private var router = FlareRouter()
    @StateObject private var composeManager = ComposeManager.shared
    @State private var presenter = ActiveAccountPresenter()

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

                        FlareMenuContainer(
                            content: HomeContent(accountType: accountType),
                            appState: appState,
                            router: router
                        )
                        .environmentObject(appState)
                        .environmentObject(router)
                        .sheet(isPresented: $router.isSheetPresented) {
                            if let destination = router.activeDestination {
                                FlareDestinationView(
                                    destination: destination,
                                    router: router,
                                    appState: appState
                                )
                            }
                        }
                        .fullScreenCover(isPresented: $router.isFullScreenPresented) {
                            if let destination = router.activeDestination {
                                FlareDestinationView(
                                    destination: destination,
                                    router: router,
                                    appState: appState
                                )
                            }
                        }
                        .alert(isPresented: $router.isDialogPresented) {
                            Alert(
                                title: Text("OK"),
                                message: Text("Are you sure ?"),
                                primaryButton: .destructive(Text("OK")) {
                                    // 处理确认操作
                                },
                                secondaryButton: .cancel(Text("Cancel"))
                            )
                        }
                        .sheet(isPresented: $composeManager.showCompose) {
                            if let accountType = composeManager.composeAccountType {
                                NavigationView {
                                    ComposeScreen(
                                        onBack: {
                                            composeManager.dismiss()
                                        },
                                        accountType: accountType,
                                        status: convertToSharedComposeStatus(composeManager.composeStatus)
                                    )
                                }
                            }
                        }
                    } else {
                        ProgressView()
                    }
                }
                .navigationViewStyle(StackNavigationViewStyle())
                .onAppear {
                    setupInitialState()

                    router.appState = appState
                }
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

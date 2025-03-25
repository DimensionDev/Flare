import shared
import SwiftUI

struct FlareRootView: View {
    @StateObject var appState = FlareAppState()
    @StateObject private var router = FlareRouter()
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
                        FlareMenuContainer(
                            content: HomeContent(accountType: accountType),
                            appState: appState,
                            router: router
                        )
                        .environmentObject(appState)
                        .environmentObject(router)
                        .sheet(isPresented: $router.isSheetPresented) {
                            if let destination = router.activeDestination {
                                FlareDestinationView(destination: destination, appState: appState)
                                    .environmentObject(router)
                            }
                        }
                        .fullScreenCover(isPresented: $router.isFullScreenPresented) {
                            if let destination = router.activeDestination {
                                FlareDestinationView(destination: destination, appState: appState)
                                    .environmentObject(router)
                            }
                        }
                        .alert(isPresented: $router.isDialogPresented) {
                            Alert(
                                title: Text("OK"),
                                message: Text("您确定要执行此操作吗？"),
                                primaryButton: .destructive(Text("OK")) {
                                    // 处理确认操作
                                },
                                secondaryButton: .cancel(Text("Cancel"))
                            )
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
}

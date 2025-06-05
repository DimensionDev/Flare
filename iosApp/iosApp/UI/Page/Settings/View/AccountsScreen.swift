import shared
import SwiftUI

struct AccountsScreen: View {
    @State private var presenter = AccountsPresenter()
    @State var showServiceSelectSheet = false
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                switch onEnum(of: state.accounts) {
                case let .success(data):
                    if data.data.size > 0 {
                        ForEach(0 ..< data.data.size, id: \.self) { index in
                            let item = data.data.get(index: index)
                            switch onEnum(of: item.second) {
                            case let .success(user):
                                Button {
                                    NotificationCenter.default.post(name: .accountChanged, object: nil)
                                    state.setActiveAccount(accountKey: user.data.key)
                                } label: {
                                    HStack {
                                        UserComponent(user: user.data, topEndContent: nil)
                                        Spacer()
                                        switch onEnum(of: state.activeAccount) {
                                        case let .success(activeAccount):
                                            Image(
                                                systemName: activeAccount.data.accountKey == user.data.key ?
                                                    "checkmark.circle.fill" :
                                                    "circle"
                                            )
                                            .foregroundStyle(theme.tintColor)
                                        default:
                                            Image(systemName: "circle")
                                                .foregroundStyle(theme.tintColor)
                                        }
                                    }
                                    .swipeActions(edge: .trailing) {
                                        Button(role: .destructive) {
                                            state.removeAccount(accountKey: user.data.key)
                                        } label: {
                                            Label("delete", systemImage: "trash")
                                        }
                                    }
                                }
                                .buttonStyle(.plain)
                                #if os(macOS)
                                    .contextMenu {
                                        Button(role: .destructive) {
                                            state.removeAccount(accountKey: user.data.key)
                                        } label: {
                                            Label("delete", systemImage: "trash")
                                        }
                                    }
                                #endif
                            case .error:
                                Text("error")
                            case .loading:
                                Text("loading")
                            case .none:
                                EmptyView()
                            }
                        }.scrollContentBackground(.hidden)
                            .listRowBackground(theme.primaryBackgroundColor)

                    } else {
                        Text("no_accounts")
                    }
                case .error:
                    Text("error")
                case .loading:
                    Text("loading")
                }
            }
            .environment(\.defaultMinListRowHeight, 90)
//            .listSectionSpacing(80)
            .listStyle(.sidebar)
            .navigationTitle("settings_accounts_title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        showServiceSelectSheet = true
                    }, label: {
                        Image(systemName: "plus").foregroundColor(theme.tintColor)
                    })
                }
            }
            .sheet(isPresented: $showServiceSelectSheet, content: {
                ServiceSelectScreen {
                    showServiceSelectSheet = false
                }
                #if os(macOS)
                .frame(minWidth: 600, minHeight: 400)
                #endif
            })
        }.scrollContentBackground(.hidden)
            .background(theme.secondaryBackgroundColor)
    }
}

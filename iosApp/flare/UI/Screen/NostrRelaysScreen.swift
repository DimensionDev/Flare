import SwiftUI
@preconcurrency import KotlinSharedUI
import FlareUI

struct NostrRelaysScreen: View {
    let accountKey: MicroBlogKey
    @StateObject private var presenter: KotlinPresenter<NostrRelaysPresenterState>
    @State private var showAddAlert = false
    @State private var relay = ""

    var body: some View {
        List {
            StateView(state: presenter.state.relays) { relaysAny in
                let relays = (relaysAny as NSArray).cast(NSString.self).map(String.init)
                if relays.isEmpty {
                    Text("No relays configured")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(relays, id: \.self) { item in
                        Text(item)
                            .swipeActions {
                                Button(role: .destructive) {
                                    presenter.state.removeRelay(relay: item)
                                } label: {
                                    Label {
                                        Text("Delete")
                                    } icon: {
                                        Image("fa-trash")
                                    }
                                }
                            }
                            .contextMenu {
                                Button(role: .destructive) {
                                    presenter.state.removeRelay(relay: item)
                                } label: {
                                    Label {
                                        Text("Delete")
                                    } icon: {
                                        Image("fa-trash")
                                    }
                                }
                            }
                    }
                }
            } errorContent: { _ in
                Text("No relays configured")
                    .foregroundStyle(.secondary)
            } loadingContent: {
                ProgressView()
            }
        }
        .navigationTitle("Manage relays")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showAddAlert = true
                } label: {
                    Image("fa-plus")
                }
            }
        }
        .alert("Add relay", isPresented: $showAddAlert) {
            TextField("Enter relay URL", text: $relay)
                .textInputAutocapitalization(.never)
#if os(iOS)
                .autocorrectionDisabled()
#endif
            Button("Cancel", role: .cancel) {
                relay = ""
            }
            Button("OK") {
                let value = relay.trimmingCharacters(in: .whitespacesAndNewlines)
                if !value.isEmpty {
                    presenter.state.addRelay(relay: value)
                }
                relay = ""
            }
        }
    }
}

extension NostrRelaysScreen {
    init(accountKey: MicroBlogKey) {
        self.accountKey = accountKey
        self._presenter = .init(wrappedValue: .init(presenter: NostrRelaysPresenter(accountKey: accountKey)))
    }
}

import SwiftUI

struct SettingsScreen: View {
    @State private var selectedDetail: SettingsDestination? = nil
    var body: some View {
        NavigationSplitView {
            List(SettingsDestination.allCases, selection: $selectedDetail) { item in
                ZStack {
                    switch item {
                    case .account:
                        ListItem(systemIconName: "person.circle", title: "Accounts")
                    }
                }
                .tag(item)
            }
            .navigationTitle("Settings")
        } detail: {
            if let detail = selectedDetail {
                switch detail {
                case .account:
                    AccountsScreen()
                }
            } else {
                Text("Settings")
            }
        }
    }
}

struct ListItem: View {
    let systemIconName: String
    let title: String
    var body: some View {
        HStack {
            Image(systemName: systemIconName)
            Text(title)
        }
    }
}

public enum SettingsDestination: String, CaseIterable, Identifiable {
    case account
    
    public var id: String {
        self.rawValue
    }
}

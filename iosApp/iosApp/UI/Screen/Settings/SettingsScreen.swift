import SwiftUI

struct SettingsScreen: View {
    var body: some View {
        List {
            NavigationLink(value: TabDestination.accountSettings) {
                ListItem(systemIconName: "person.circle", title: "Accounts")
            }
        }
        .navigationTitle("Settings")
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

#Preview {
    ListItem(systemIconName: "person.circle", title: "Accounts")
}

#Preview {
    NavigationStack {
        SettingsScreen()
            .withTabRouter()
    }
}

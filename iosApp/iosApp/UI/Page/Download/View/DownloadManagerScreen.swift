import Foundation
import SwiftUI
import shared

struct DownloadManagerScreen: View {
    @ObservedObject var router: FlareRouter
    @EnvironmentObject private var menuState: FlareAppState
    
    let accountType: AccountType
    
    init(accountType: AccountType, router: FlareRouter) {
        self.accountType = accountType
        self.router = router
    }
    
    var body: some View {
        List {
            Text("Donwload")
                .listRowBackground(Colors.Background.swiftUIPrimary)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Colors.Background.swiftUIPrimary)
        .navigationTitle("Donwload")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Colors.Background.swiftUIPrimary, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .environmentObject(router)
        .environmentObject(menuState)
    }
} 
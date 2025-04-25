import SwiftUI

struct NotificationsView: View {
    @EnvironmentObject private var appState: AppState
 
    var body: some View {
        NavigationView {
             List {
                 ForEach(0..<50) { index in
                     Text("Notification \(index)")
                 }
             }
             .listStyle(.plain)
             .navigationTitle("Notifications")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
       
        .standardMenuGestureHandler() // <<< 应用 Modifier
        .onAppear {
//            appState.isHomeFirstTabActive = false
            appState.navigationDepth = 0
            appState.menuDragOffset = 0
            print("NotificationsView appeared, depth: \(appState.navigationDepth)")
        }
    }
}
 

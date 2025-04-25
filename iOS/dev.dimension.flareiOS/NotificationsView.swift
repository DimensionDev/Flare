import SwiftUI

struct NotificationsView: View {
    @EnvironmentObject private var appState: AppState
 
    var body: some View {
        NavigationView {
             Text("Notifications Content Area")
                 .navigationTitle("Notifications")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.orange.opacity(0.1))
       
        .standardMenuGestureHandler() // <<< 应用 Modifier
        .onAppear {
//            appState.isHomeFirstTabActive = false
            appState.navigationDepth = 0
            appState.menuDragOffset = 0
            print("NotificationsView appeared, depth: \(appState.navigationDepth)")
        }
    }
}
 

import SwiftUI

struct MessagesView: View {
    @EnvironmentObject private var appState: AppState
 
    var body: some View {
        NavigationView {
             Text("Messages Content Area")
                 .navigationTitle("Messages")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.green.opacity(0.1))
       
        .standardMenuGestureHandler() // <<< 应用 Modifier
        .onAppear {
//            appState.isHomeFirstTabActive = false
            appState.navigationDepth = 0
            appState.menuDragOffset = 0
            print("MessagesView appeared, depth: \(appState.navigationDepth)")
        }
    }
}
 

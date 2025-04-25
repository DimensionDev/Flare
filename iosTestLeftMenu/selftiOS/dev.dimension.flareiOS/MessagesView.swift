import SwiftUI

struct MessagesView: View {
    @EnvironmentObject private var appState: AppState
 
    var body: some View {
        NavigationView {
             List {
                 ForEach(0..<50) { index in
                     Text("Message \(index)")
                 }
             }
             .listStyle(.plain)
             .navigationTitle("Messages")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .standardMenuGestureHandler() // <<< 应用 Modifier
        .onAppear {
//            appState.isHomeFirstTabActive = false
            appState.navigationDepth = 0
            appState.menuDragOffset = 0
            print("MessagesView appeared, depth: \(appState.navigationDepth)")
        }
    }
}
 

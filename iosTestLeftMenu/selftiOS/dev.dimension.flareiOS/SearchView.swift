import SwiftUI

struct SearchView: View {
    @EnvironmentObject private var appState: AppState
 
    var body: some View {
        NavigationView {
             List {
                 ForEach(0..<50) { index in
                     Text("Search Result \(index)")
                 }
             }
             .listStyle(.plain)
             .navigationTitle("Search")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
      
        // --- 应用新的 Modifier ---
        .standardMenuGestureHandler()  
        .onAppear {
//            appState.isHomeFirstTabActive = false
            appState.navigationDepth = 0
            appState.menuDragOffset = 0
            print("SearchView appeared, depth: \(appState.navigationDepth)")
        }
    }
}

 

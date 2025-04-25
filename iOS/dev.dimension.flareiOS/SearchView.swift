import SwiftUI

struct SearchView: View {
    @EnvironmentObject private var appState: AppState
 
    var body: some View {
        NavigationView {
             Text("Search Content Area")
                 .navigationTitle("Search")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.cyan.opacity(0.1))
      
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

 

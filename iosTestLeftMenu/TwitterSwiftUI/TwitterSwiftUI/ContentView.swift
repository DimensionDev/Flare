//
//  ContentView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/21.
//

import SwiftUI
import Kingfisher

struct ContentView: View {

    @StateObject var viewModel: ContentViewModel
    
    init() {
        _viewModel = .init(wrappedValue: ContentViewModel())
    }

    var body: some View {
    
        // if viewModel.userSession == nil {
        //     EntryView()
        // } else {
            MainTabView()
                .environmentObject(MainTabBarViewModel())
        // }
    }
}

#Preview {
    ContentView()
}

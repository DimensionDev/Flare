//
//  dev_dimension_flareiOSApp.swift
//  dev.dimension.flareiOS
//
//  Created by abujj on 4/24/25.
//

import SwiftUI

@main
struct dev_dimension_flareiOSApp: App {
    // 创建 AppState 的单一实例，并在视图层级中共享
    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            MainView()
                .environmentObject(appState) // 将 appState 注入环境
        }
    }
}

//
//  TwitterSwiftUIApp.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/21.
//

import SwiftUI
// import Firebase

//class AppDelegate: NSObject, UIApplicationDelegate {
//  func application(_ application: UIApplication,
//                   didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
//    FirebaseApp.configure()
//
//    return true
//  }
//}
//
//
//@main
//struct TwitterSwiftUIApp: App {
//    // register app delegate for Firebase setup
//    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
//
//    var body: some Scene {
//        WindowGroup {
//            NavigationView {
//                LoginView()
//               // ContentView()
//            }
//        }
//    }
//}


@main
struct TwitterSwiftUIApp: App {
    
    // 上のinit処理を下の一行で完了
//    init() {
//        FirebaseApp.configure()
//    }
//    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

//
//  EntryViewModel.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/10/08.
//

import SwiftUI

enum AuthButtonType: Int, CaseIterable, Identifiable {
    case signInWithGoole
    case signInWithApple
    case createAccount
    
    var title: String {
        switch self {
        case .signInWithGoole: "Googleのアカウントで続ける"
        case .signInWithApple: "Appleのアカウントで続ける"
        case .createAccount: "アカウントを作成"
        }
    }
    
    var image: String? {
        switch self {
        case .signInWithGoole: "google"
        case .signInWithApple: "apple"
        case .createAccount: nil
        }
    }
    
    var backgroundColor: Color {
        switch self {
        case .signInWithGoole, .signInWithApple: .white
        case .createAccount: .black
        }
    }
    
    var id: Int { self.rawValue }
}

class EntryViewModel: ObservableObject {
    
}

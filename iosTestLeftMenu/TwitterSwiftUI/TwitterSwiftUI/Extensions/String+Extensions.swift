//
//  String+Extensions.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/21.
//

import Foundation

extension String {
    var emailUsername: String? {
        self.split(separator: "@").first.map(String.init)
    }
}

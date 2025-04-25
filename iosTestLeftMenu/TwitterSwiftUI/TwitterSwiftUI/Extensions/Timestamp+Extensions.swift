//
//  Timestamp+Extensions.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/18.
//

import Foundation
import Firebase

extension Timestamp {
    func timesString() -> String {
        self.dateValue()
        let formatter = DateComponentsFormatter()
        formatter.allowedUnits = [.second, .minute, .hour, .day, .weekOfMonth]
        formatter.maximumUnitCount = 1 // 時間の単位は1つに絞る (例：1h 30mではなく、1h)
        formatter.unitsStyle = .abbreviated // 表示スタイル例） 1h or 30m とか
        return formatter.string(from: self.dateValue(), to: Date()) ?? ""
    }
    
    func timeStringJP() -> String {
                
        let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: self.dateValue(), to: Date())
        
        if let years = components.year, years > 0 {
            return "\(years)年前"
        }
        
        if let months = components.month, months > 0 {
            return "\(months)ヶ月前"
        }
        
        if let days = components.day, days > 0 {
            return "\(days)日前"
        }
        
        if let hours = components.hour, hours > 0 {
            return "\(hours)時間前"
        }
        
        if let minutes = components.minute, minutes > 0 {
            return "\(minutes)分前"
        }
        
        if let seconds = components.second, seconds > 0 {
            return "\(seconds)秒前"
        }
        
        return "たった今"
    }
}

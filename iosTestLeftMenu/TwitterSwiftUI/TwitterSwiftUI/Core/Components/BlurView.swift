//
//  BlurView.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/15.
//

import SwiftUI

struct BlurView: UIViewRepresentable {
  
    var style: UIBlurEffect.Style = .light
    
    func makeUIView(context: Context) -> UIVisualEffectView {
        
        let effect = UIBlurEffect(style: style)
        
        let effetView = UIVisualEffectView(effect: effect)
        
        return effetView
    }
    
    func updateUIView(_ uiView: UIVisualEffectView, context: Context) { }
}

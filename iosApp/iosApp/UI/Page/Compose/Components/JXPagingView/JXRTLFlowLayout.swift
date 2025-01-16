//
//  JXRTLFlowLayout.swift
//  JXRTLFlowLayout
//
//  Created by jx on 2024/5/27.
//

import UIKit

class JXRTLFlowLayout: UICollectionViewFlowLayout {
    override var flipsHorizontallyInOppositeLayoutDirection: Bool {
        UIView.userInterfaceLayoutDirection(for: UIView.appearance().semanticContentAttribute) == .rightToLeft
    }
}

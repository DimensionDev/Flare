//
//  ShareActivityProvider.swift
//  TwidereX
//
//  Created by MainasuK on 2021-12-7.
//  Copyright © 2021 Twidere. All rights reserved.
//

import UIKit

public protocol ShareActivityProvider {
    var activities: [Any] { get }
    var applicationActivities: [UIActivity] { get }
}

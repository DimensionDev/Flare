//
//  ColPosition.swift
//  ZJFlexibleLayoutDemo
//
//  Created by ZhuFaner on 2017/10/24.
//  Copyright © 2017年 ZhijieZhang. All rights reserved.
//

import UIKit

// 用来储存某一item的的纵坐标
typealias ColY = (index: Int, colY: CGFloat)

// 存储某一列的所有item的位置信息
struct ColPosition {
    var colYs: [ColY]

    // 每个section的高度：返回最大值
    var maxY: CGFloat {
        if let max = colYs.max(by: { $0.colY < $1.colY }) {
            return max.colY
        }
        return 0
    }
}

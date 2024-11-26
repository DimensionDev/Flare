//
//  ZJFlexibleLayoutDataSource.swift
//  ZJFlexibleLayoutDemo
//
//  Created by ZhuFaner on 2017/10/24.
//  Copyright © 2017年 ZhijieZhang. All rights reserved.
//

import UIKit

public protocol ZJFlexibleDataSource: class{
    //控制对应section的瀑布流列数
    func numberOfCols(at section: Int) -> Int
    //控制每个cell的尺寸，实际上就是获取宽高比
    func sizeOfItemAtIndexPath(at indexPath : IndexPath) -> CGSize
    //控制瀑布流cell的间距
    func spaceOfCells(at section: Int) -> CGFloat
    //边距
    func sectionInsets(at section: Int) -> UIEdgeInsets
    //每个section的header尺寸
    func sizeOfHeader(at section: Int) -> CGSize
    //每个cell的额外高度
    func heightOfAdditionalContent(at indexPath : IndexPath) -> CGFloat
}

extension ZJFlexibleDataSource{
    func spaceOfCells(at section: Int) -> CGFloat{
        return 0
    }
    
    func sectionInsets(at section: Int) -> UIEdgeInsets{
        return UIEdgeInsets.zero
    }
    
    func sizeOfHeader(at section: Int) -> CGSize{
        return CGSize.zero
    }
    
    func heightOfAdditionalContent(at indexPath : IndexPath) -> CGFloat{
        return 0
    }
}

import SwiftUI
import UIKit

extension UIView {
    private enum AssociatedKeys {
        static var gestureCoordinator = "FLNewGestureCoordinator"
    }

    // 获取手势协调器
    private var gestureCoordinator: FLNewGestureCoordinator? {
        get {
            objc_getAssociatedObject(
                self,
                &AssociatedKeys.gestureCoordinator
            ) as? FLNewGestureCoordinator
        }
        set {
            objc_setAssociatedObject(
                self,
                &AssociatedKeys.gestureCoordinator,
                newValue,
                .OBJC_ASSOCIATION_RETAIN_NONATOMIC
            )
        }
    }

    // 添加菜单手势
    func addNewMenuGesture(
        gestureState: FLNewGestureState,
        menuState: Binding<Bool>,
        tabProvider: TabStateProvider? = nil
    ) {
        // 创建手势协调器
        let coordinator = FLNewGestureCoordinator(
            gestureState: gestureState,
            menuState: menuState,
            tabProvider: tabProvider
        )

        // 保存协调器引用
        gestureCoordinator = coordinator

        // 添加手势识别器
        let gestureRecognizer = coordinator.createGestureRecognizer()
        addGestureRecognizer(gestureRecognizer)
    }

    // 移除菜单手势
    func removeNewMenuGesture() {
        gestureRecognizers?.forEach { recognizer in
            if recognizer is FLNewGestureRecognizer {
                removeGestureRecognizer(recognizer)
            }
        }
        gestureCoordinator = nil
    }
}

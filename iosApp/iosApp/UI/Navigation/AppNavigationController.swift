import SwiftUI
import UIKit

class AppNavigationController: UINavigationController, ObservableObject {
    // MARK: - Properties

    private var interactiveTransition: UIPercentDrivenInteractiveTransition?
    private var panGestureRecognizer: UIPanGestureRecognizer?
    private var isTransitioning = false

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        setupCustomNavigation()
    }

    // MARK: - Setup

    private func setupCustomNavigation() {
        delegate = self

        // 完全禁用系统返回手势
        interactivePopGestureRecognizer?.isEnabled = false

        // 添加自定义全屏返回手势
        let panGesture = UIPanGestureRecognizer(target: self, action: #selector(handlePanGesture(_:)))
        panGesture.delegate = self // 设置手势代理
        view.addGestureRecognizer(panGesture)
        panGestureRecognizer = panGesture

        // 设置导航栏样式
        navigationBar.isTranslucent = true
        view.backgroundColor = .clear
        navigationBar.setBackgroundImage(UIImage(), for: .default)
        navigationBar.shadowImage = UIImage()
        navigationBar.tintColor = .black
    }

    // MARK: - Gesture Handling

    @objc private func handlePanGesture(_ gesture: UIPanGestureRecognizer) {
        // 获取手势在水平方向的移动距离
        let translation = gesture.translation(in: gesture.view)
        let velocity = gesture.velocity(in: gesture.view)

        // 计算进度（0-1之间）
        let progress = max(0, min(1, translation.x / view.bounds.width))

        switch gesture.state {
        case .began:
            // 只有当可以返回且没有正在进行的转场时才开始
            guard viewControllers.count > 1, !isTransitioning else { return }
            isTransitioning = true

            // 创建交互式转场控制器
            interactiveTransition = UIPercentDrivenInteractiveTransition()
            interactiveTransition?.completionCurve = .easeInOut

            // 开始返回操作
            popViewController(animated: true)

        case .changed:
            // 更新转场进度
            interactiveTransition?.update(progress)

        case .ended, .cancelled:
            isTransitioning = false

            // 根据进度和速度决定是否完成转场
            let shouldComplete = progress > 0.5 || velocity.x > 800

            if shouldComplete {
                interactiveTransition?.completionSpeed = 0.7
                interactiveTransition?.finish()
            } else {
                interactiveTransition?.completionSpeed = 0.3
                interactiveTransition?.cancel()
            }
            interactiveTransition = nil

        default:
            isTransitioning = false
            interactiveTransition?.cancel()
            interactiveTransition = nil
        }
    }

    // MARK: - Navigation Methods

//    func navigateTo(route: AppRoute) {
//        AppNavigation.shared.navigate(route, source: self)
//    }
//
//    func presentScreen(route: AppRoute) {
//        AppNavigation.shared.present(route, source: self)
//    }
//
//    func presentModally(route: AppRoute) {
//        AppNavigation.shared.presentModally(route, source: self)
//    }
}

// MARK: - UIGestureRecognizerDelegate

extension AppNavigationController: UIGestureRecognizerDelegate {
    // 控制手势何时可以开始
    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let panGesture = gestureRecognizer as? UIPanGestureRecognizer else { return false }

        // 只允许从左向右滑动
        let translation = panGesture.translation(in: view)
        let isHorizontalPan = abs(translation.x) > abs(translation.y)
        let isRightDirection = translation.x > 0

        // 确保有可返回的控制器且不在转场过程中
        return viewControllers.count > 1 && !isTransitioning && isHorizontalPan && isRightDirection
    }
}

// MARK: - UINavigationControllerDelegate

extension AppNavigationController: UINavigationControllerDelegate {
    func navigationController(_: UINavigationController,
                              animationControllerFor operation: UINavigationController.Operation,
                              from _: UIViewController,
                              to _: UIViewController) -> UIViewControllerAnimatedTransitioning?
    {
        CustomNavigationAnimator(operation: operation)
    }

    func navigationController(_: UINavigationController,
                              interactionControllerFor _: UIViewControllerAnimatedTransitioning) -> UIViewControllerInteractiveTransitioning?
    {
        interactiveTransition
    }
}

// MARK: - 自定义导航动画

private class CustomNavigationAnimator: NSObject, UIViewControllerAnimatedTransitioning {
    private let operation: UINavigationController.Operation

    init(operation: UINavigationController.Operation) {
        self.operation = operation
        super.init()
    }

    func transitionDuration(using _: UIViewControllerContextTransitioning?) -> TimeInterval {
        0.18
    }

    func animateTransition(using transitionContext: UIViewControllerContextTransitioning) {
        guard let fromVC = transitionContext.viewController(forKey: .from),
              let toVC = transitionContext.viewController(forKey: .to),
              let fromView = fromVC.view,
              let toView = toVC.view
        else {
            transitionContext.completeTransition(false)
            return
        }

        let containerView = transitionContext.containerView
        let duration = transitionDuration(using: transitionContext)

        // 设置初始位置
        if operation == .push {
            containerView.addSubview(toView)
            toView.frame = containerView.bounds.offsetBy(dx: containerView.bounds.width, dy: 0)
        } else {
            containerView.insertSubview(toView, belowSubview: fromView)
            toView.frame = containerView.bounds.offsetBy(dx: -containerView.bounds.width * 0.3, dy: 0)
        }

        // 添加阴影效果
        let viewToAddShadow = operation == .push ? toView : fromView
        viewToAddShadow.layer.shadowColor = UIColor.black.cgColor
        viewToAddShadow.layer.shadowOpacity = 0.3
        viewToAddShadow.layer.shadowRadius = 4

        // 执行动画
        UIView.animate(withDuration: duration, delay: 0, options: [.curveEaseInOut]) {
            if self.operation == .push {
                fromView.frame = containerView.bounds.offsetBy(dx: -containerView.bounds.width * 0.3, dy: 0)
                toView.frame = containerView.bounds
            } else {
                fromView.frame = containerView.bounds.offsetBy(dx: containerView.bounds.width, dy: 0)
                toView.frame = containerView.bounds
            }
        } completion: { _ in
            // 清理阴影效果
            viewToAddShadow.layer.shadowOpacity = 0

            let success = !transitionContext.transitionWasCancelled
            if !success {
                toView.removeFromSuperview()
            }
            transitionContext.completeTransition(success)
        }
    }
}

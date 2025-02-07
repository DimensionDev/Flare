import Foundation

protocol FLNewSideMenuDelegate: AnyObject {
    func menuWillOpen()
    func menuDidOpen()
    func menuWillClose()
    func menuDidClose()

    // 默认实现
    func handleMenuStateChange(_ isOpen: Bool)
}

// 提供默认实现
extension FLNewSideMenuDelegate {
    func menuWillOpen() {}
    func menuDidOpen() {}
    func menuWillClose() {}
    func menuDidClose() {}

    func handleMenuStateChange(_ isOpen: Bool) {
        if isOpen {
            menuWillOpen()
            menuDidOpen()
        } else {
            menuWillClose()
            menuDidClose()
        }
    }
}

import Foundation

extension NSArray {
    func cast<T>(_ type: T.Type) -> [T] {
        compactMap { $0 as? T }
    }
}

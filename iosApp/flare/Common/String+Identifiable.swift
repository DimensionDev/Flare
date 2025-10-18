import Foundation

extension String: Identifiable {
    public typealias ID = Int
    public var id: Int {
        return hash
    }
}

extension Int: Identifiable {
    public typealias ID = Int
    public var id: Int {
        return self
    }
}

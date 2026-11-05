//  Copyright (c) Facebook, Inc. and its affiliates.

// See https://forums.swift.org/t/using-indirect-modifier-for-struct-properties/37600/16
@propertyWrapper
public indirect enum Indirect<T> {
    case wrapped(T)

    public init(wrappedValue initialValue: T) {
        self = .wrapped(initialValue)
    }

    public var wrappedValue: T {
        get {
            switch self {
            case .wrapped(let x): return x
            }
        }
        set { self = .wrapped(newValue) }
    }
}

extension Indirect: Equatable where T: Equatable {}
extension Indirect: Hashable where T: Hashable {}

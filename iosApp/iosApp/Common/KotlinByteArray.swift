import Foundation
import shared

extension KotlinByteArray {
    static func from(data: Data) -> KotlinByteArray {
        let swiftByteArray = [UInt8](data)
        return swiftByteArray
            .map(Int8.init(bitPattern:))
            .enumerated()
            .reduce(into: KotlinByteArray(size: Int32(swiftByteArray.count))) { result, row in
                result.set(index: Int32(row.offset), value: row.element)
            }
    }
}

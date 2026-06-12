import SwiftUI
import KotlinSharedUI

public struct DateTimeText: View {
    @Environment(\.timelineAppearance.absoluteTimestamp) private var absoluteTimestamp
    private let data: UiDateTime
    private let fullTime: Bool

    public init(data: UiDateTime, fullTime: Bool) {
        self.data = data
        self.fullTime = fullTime
    }

    public var body: some View {
        if fullTime {
            Text(data.full)
        } else if data.shouldShowFull {
            Text(data.platformValue, style: .date)
        } else if absoluteTimestamp {
            Text(data.absolute)
        } else {
            Text(data.platformValue, style: .relative)
        }
    }
}

public extension DateTimeText {
    init(data: UiDateTime) {
        self.init(data: data, fullTime: false)
    }
}

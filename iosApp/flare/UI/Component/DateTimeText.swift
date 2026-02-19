import SwiftUI
import KotlinSharedUI

struct DateTimeText: View {
    @Environment(\.appearanceSettings.absoluteTimestamp) private var absoluteTimestamp
    let data: UiDateTime
    let fullTime: Bool

    var body: some View {
        if fullTime {
            Text(data.platformValue, style: .date) + Text(data.platformValue, style: .time)
        } else if data.shouldShowFull {
            Text(data.platformValue, style: .date)
        } else if absoluteTimestamp {
            Text(data.absolute)
        } else {
            Text(data.platformValue, style: .relative)
        }
    }
}

extension DateTimeText {
    init(data: UiDateTime) {
        self.data = data
        self.fullTime = false
    }
}

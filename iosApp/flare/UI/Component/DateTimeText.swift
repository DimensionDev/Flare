import SwiftUI
import KotlinSharedUI

struct DateTimeText: View {
    let data: UiDateTime
    let fullTime: Bool

    var body: some View {
        if fullTime {
            Text(data.platformValue, style: .date) + Text(data.platformValue, style: .time)
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

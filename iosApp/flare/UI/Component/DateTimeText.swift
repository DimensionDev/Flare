import SwiftUI
import KotlinSharedUI

struct DateTimeText: View {
    let data: UiDateTime
    let fullTime: Bool

    var body: some View {
        if fullTime {
            HStack {
                Text(data.platformValue, style: .date)
                Text(data.platformValue, style: .time)
            }
        } else {
            switch data.diff {
            case .days: Text(data.platformValue, style: .relative)
            case .hours: Text(data.platformValue, style: .relative)
            case .minutes: Text(data.platformValue, style: .relative)
            case .seconds: Text(data.platformValue, style: .relative)
            case .monthDay: Text(data.platformValue, style: .date)
            case .yearMonthDay: Text(data.platformValue, style: .date)
            }
        }
    }
}

extension DateTimeText {
    init(data: UiDateTime) {
        self.data = data
        self.fullTime = false
    }
}

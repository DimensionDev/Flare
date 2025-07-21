import SwiftUI

class TimelineExtState: ObservableObject {
    @Published var scrollToTopTrigger = false
    @Published var showFloatingButton = false 
}

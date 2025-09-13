import SwiftUI
import KotlinSharedUI
import Awesome

struct StatusVisibilityView: View {
    let data: UiTimeline.ItemContentStatusTopEndContentVisibilityType
    var body: some View {
        switch data {
        case .public: Awesome.Classic.Solid.globe.image
        case .home: Awesome.Classic.Solid.lockOpen.image
        case .followers: Awesome.Classic.Solid.lock.image
        case .specified: Awesome.Classic.Solid.at.image
        }
    }
}

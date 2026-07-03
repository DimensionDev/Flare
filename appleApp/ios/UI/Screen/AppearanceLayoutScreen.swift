import SwiftUI
import FlareAppleUI

struct AppearanceLayoutScreen: View {
    var body: some View {
        List {
            AppearanceLayoutSettingsSection {
                NavigationLink(value: Route.postActionLayout) {
                    VStack(alignment: .leading) {
                        Label {
                            Text("Customize actions")
                            Text("Choose the order and visibility of post action buttons")
                        } icon: {
                            EmptyView()
                        }

                    }
                }
            }
        }
        .navigationTitle("appearance_layout_group_title")
    }
}

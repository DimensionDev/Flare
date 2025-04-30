import shared
import SwiftUI

struct SpaceScreen: View {
    let accountType: AccountType
    @EnvironmentObject private var router: FlareRouter
    @State private var podcastIdInput: String = ""

    var body: some View {
        VStack(spacing: 20) {
            Text("XSpace Test")
                .font(.largeTitle)
                .padding(.top)

            HStack {
                Text("Spaces list comming soon")
                Spacer()
                TextField("Enter Podcast/Space ID", text: $podcastIdInput)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)

                Button("Confirm") {
                    guard !podcastIdInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                    router.navigate(to: .podcastSheet(accountType: accountType, podcastId: podcastIdInput))
                }
                .buttonStyle(.borderedProminent)
                .disabled(podcastIdInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.horizontal)

            Button("Set Test Replay ID") {
                podcastIdInput = "1jMJgkygVkXJL"
            }
            .buttonStyle(.bordered)

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .navigationTitle("XSpace Test")
        .navigationBarTitleDisplayMode(.inline)
    }
}

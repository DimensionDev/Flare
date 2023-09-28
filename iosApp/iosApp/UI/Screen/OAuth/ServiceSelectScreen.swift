import SwiftUI

struct ServiceSelectScreen: View {
    let toMisskey: () -> Void
    let toMastodon: () -> Void
    var body: some View {
        VStack(alignment:.center, spacing: 10) {
            Spacer()
                .frame(height: 96)
            Text("Welcome to Flare")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.black)
            Text("Flare is a social network client for iOS.\nTo get started, please select a service to connect to.")
                .font(.subheadline)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
            Spacer()
                .frame(height: 16)
            Button(action: toMastodon) {
                Text("Mastodon")
                    .frame(maxWidth: .infinity)
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .padding()
                    .background(Color.blue)
                    .cornerRadius(10)
                    .scenePadding([.leading,.trailing])
            }
            Button(action: toMisskey) {
                Text("Misskey")
                    .frame(maxWidth: .infinity)
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .padding()
                    .background(Color.green)
                    .cornerRadius(10)
                    .scenePadding([.leading,.trailing])
            }
        }.frame(maxHeight: .infinity, alignment: .top)
    }
}



#Preview {
    ServiceSelectScreen(
        toMisskey: {}, toMastodon: {}
    )
}

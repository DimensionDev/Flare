import SwiftUI

struct AboutScreen: View {
    var body: some View {
        let versionName = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let versionCode = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? ""
        ScrollView {
            VStack {
                Image(.logo)
                    .resizable()
                    .frame(width: 96, height: 96)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .clipped()
                    .padding()
                Text("Flare")
                    .font(.title)
                Text("The ultimate next generation open-sourced AI powered decentralized social network client for Apple.")
                    .multilineTextAlignment(.center)
                HStack {
                    Text(versionName)
                    Text("("+versionCode+")")
                }
                .opacity(0.5)
                .font(.caption)
                Link(
                    destination: URL(string: "https://github.com/DimensionDev/Flare")!
                ) {
                    HStack {
                        AsyncImage(
                            url: URL(string: "https://github.githubassets.com/assets/GitHub-Mark-ea2971cee799.png")
                        ) { image in
                            image.image?.resizable()
                        }
                        .frame(width: 48, height: 48)
                        VStack(alignment: .leading) {
                            Text("Source code")
                            Text("https://github.com/DimensionDev/Flare")
                        }
                        Spacer()
                    }
                }
                .buttonStyle(.plain)
            }
            .padding()
        }
        .navigationTitle("About")
    }
}

#Preview {
    NavigationStack {
        AboutScreen()
    }
}

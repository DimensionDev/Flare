import SwiftUI

/// Lets people who sign in to X with Google or Apple log in by pasting the session
/// cookies from a browser, since Google blocks its sign-in inside embedded web views.
public struct XCookieImportView: View {
    @State private var isExpanded = false
    @State private var authToken = ""
    @State private var ct0 = ""
    private let onSubmit: (String) -> Void

    public init(onSubmit: @escaping (String) -> Void) {
        self.onSubmit = onSubmit
    }

    public static func isXLoginURL(_ url: String) -> Bool {
        guard let host = URL(string: url)?.host?.lowercased() else { return false }
        return host == "x.com" || host.hasSuffix(".x.com") || host == "twitter.com" || host.hasSuffix(".twitter.com")
    }

    public var body: some View {
        DisclosureGroup(isExpanded: $isExpanded) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Log in to x.com in your browser, open its developer tools, and copy the values of the auth_token and ct0 cookies. They give full access to your account, so don't share them.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                SecureField("auth_token", text: $authToken)
                SecureField("ct0", text: $ct0)
                Button("Log in with cookies") {
                    onSubmit("auth_token=\(Self.clean(authToken)); ct0=\(Self.clean(ct0))")
                }
                .disabled(Self.clean(authToken).isEmpty || Self.clean(ct0).isEmpty)
            }
            .textFieldStyle(.roundedBorder)
            .padding(.top, 8)
        } label: {
            Text("Signed up with Google or Apple? Log in with browser cookies")
                .font(.callout)
        }
        .padding()
    }

    // Accept a bare value or a pasted "name=value" pair.
    private static func clean(_ value: String) -> String {
        var value = value.trimmingCharacters(in: .whitespacesAndNewlines.union(CharacterSet(charactersIn: ";\"")))
        if let range = value.range(of: "=") {
            value = String(value[range.upperBound...])
        }
        return value
    }
}

import Foundation

@main
struct InitializationSmoke {
    static func main() async throws {
        let directory = CommandLine.arguments[1]
        precondition(ProcessInfo.processInfo.environment["CFFIXED_USER_HOME"] == directory)
        let root = URL(fileURLWithPath: directory).appendingPathComponent(".flare")
        precondition(!FileManager.default.fileExists(atPath: root.path))
        let fixture = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[2]))
        let config = InitConfig(androidFilesDir: nil)
        do {
            try await initialize(config: InitConfig(androidFilesDir: "/android-only"))
            fatalError("invalid configuration succeeded")
        } catch InitError.invalidConfiguration(_) {}

        try FileManager.default.createDirectory(at: root.deletingLastPathComponent(), withIntermediateDirectories: true)
        try Data("blocked directory".utf8).write(to: root)
        do {
            try await initialize(config: config)
            fatalError("unavailable directory succeeded")
        } catch InitError.dataStore(_) {}
        let blocked = try Data(contentsOf: root)
        precondition(blocked == Data("blocked directory".utf8))
        try FileManager.default.removeItem(at: root)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
        let settings = root.appendingPathComponent("app_settings.pb")
        try fixture.write(to: settings)

        let cancelled = Task { try await initialize(config: config) }
        await Task.yield()
        cancelled.cancel()
        do { try await cancelled.value } catch is CancellationError {}
        try await withThrowingTaskGroup(of: Void.self) { group in
            for _ in 0..<64 {
                group.addTask { try await initialize(config: config) }
            }
            try await group.waitForAll()
        }
        try await initialize(config: config)
        let persisted = try Data(contentsOf: settings)
        precondition(persisted == fixture)
        print("Swift initialization: errors, retry, cancellation, concurrency and legacy data passed.")
    }
}

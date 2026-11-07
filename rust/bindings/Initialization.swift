
/// Wait for the process's shared Rust application to initialize.
public func initialize(config: InitConfig) async throws {
    let waiter = RustInitializationWaiter()
    try await withTaskCancellationHandler {
        try await withCheckedThrowingContinuation { continuation in
            guard waiter.install(continuation) else { return }
            do {
                try beginInitialize(config: config, observer: waiter)
            } catch {
                waiter.finish(.failure(error))
            }
        }
    } onCancel: {
        waiter.cancel()
    }
}

private final class RustInitializationWaiter: InitializationObserver, @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<Void, any Error>?
    private var cancelled = false

    func install(_ continuation: CheckedContinuation<Void, any Error>) -> Bool {
        lock.lock()
        if cancelled {
            lock.unlock()
            continuation.resume(throwing: CancellationError())
            return false
        }
        self.continuation = continuation
        lock.unlock()
        return true
    }

    func onInitialized() {
        finish(.success(()))
    }

    func onFailed(error: InitError) {
        finish(.failure(error))
    }

    func finish(_ result: Result<Void, any Error>) {
        lock.lock()
        let pending = continuation
        continuation = nil
        lock.unlock()
        pending?.resume(with: result)
    }

    func cancel() {
        lock.lock()
        cancelled = true
        let pending = continuation
        continuation = nil
        lock.unlock()
        pending?.resume(throwing: CancellationError())
    }
}

private final class RustInitializationVtableStorage: @unchecked Sendable {
    let pointer: UnsafePointer<___InitializationObserverVTable>

    init(_ value: ___InitializationObserverVTable) {
        // Rust retains this immutable callback table for the process lifetime.
        let storage = UnsafeMutablePointer<___InitializationObserverVTable>.allocate(capacity: 1)
        storage.initialize(to: value)
        pointer = UnsafePointer(storage)
    }
}

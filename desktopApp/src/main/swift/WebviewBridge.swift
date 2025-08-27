import Cocoa
import WebKit

private var nextId: Int64 = 1

private class Controller {
    let id: Int64
    let window: NSWindow
    let web: WKWebView
    var timer: DispatchSourceTimer?
    // 0 = closed by user；1 = API call wkb_close_window；2 = decision callback
    var closeReason: Int32 = 0

    init(id: Int64, window: NSWindow, web: WKWebView) {
        self.id = id
        self.window = window
        self.web = web
    }
}
private var ctrls: [Int64: Controller] = [:]

// when to close callback, return 1 to close, 0 to keep open
public typealias DecisionCB = @convention(c) (_ cookies: UnsafePointer<CChar>?) -> Int32
private var gDecision: DecisionCB?

// when window closed callback
// reason: 0 = closed by user；1 = API call wkb_close_window；2 = decision callback
public typealias WindowClosedCB = @convention(c) (_ id: Int64, _ reason: Int32) -> Void
private var gOnClosed: WindowClosedCB?

private func makeWindow(with web: WKWebView, title: String) -> NSWindow {
    let win = NSWindow(contentRect: NSRect(x: 200, y: 200, width: 1000, height: 700),
                       styleMask: [.titled, .closable, .resizable, .miniaturizable],
                       backing: .buffered, defer: false)
    win.title = title
    win.contentView = web
    win.isReleasedWhenClosed = false
    return win
}

private func startCookiePolling(for ctrl: Controller, targetURL: URL?, intervalMs: Int32) {
    let q = DispatchQueue(label: "cookie.poller.\(ctrl.id)")
    let t = DispatchSource.makeTimerSource(queue: q)
    t.schedule(deadline: .now() + .milliseconds(Int(intervalMs)),
               repeating: .milliseconds(Int(intervalMs)))
    t.setEventHandler { [weak ctrl] in
        guard let ctrl = ctrl else { return }
        let store = ctrl.web.configuration.websiteDataStore.httpCookieStore

        let sem = DispatchSemaphore(value: 0)
        var header = ""
        DispatchQueue.main.async {
            store.getAllCookies { cookies in
                var cookieString = ""
                for cookie in cookies {
                    cookieString += "\(cookie.name)=\(cookie.value); "
                }
                header = cookieString
                sem.signal()
            }
        }
        sem.wait()

        if let cb = gDecision {
            header.withCString { cstr in
                let shouldClose = cb(cstr) == 1
                if shouldClose {
                    DispatchQueue.main.async {
                        ctrl.closeReason = 2 // decision callback
                        ctrl.window.performClose(nil)
                    }
                }
            }
        }
    }
    ctrl.timer = t
    t.resume()
}

private func stopAndDispose(id: Int64) {
    if let c = ctrls[id] {
        c.timer?.cancel()
        c.timer = nil
        ctrls[id] = nil
    }
}

@_cdecl("wkb_set_decision_callback")
public func wkb_set_decision_callback(_ cb: DecisionCB?) {
    gDecision = cb
}

@_cdecl("wkb_set_window_closed_callback")
public func wkb_set_window_closed_callback(_ cb: WindowClosedCB?) {
    gOnClosed = cb
}

@_cdecl("wkb_open_webview_poll")
public func wkb_open_webview_poll(_ urlCString: UnsafePointer<CChar>?, _ intervalMs: Int32) -> Int64 {
    let urlStr = urlCString.flatMap { String(cString: $0) } ?? "about:blank"
    let targetURL = URL(string: urlStr)

    var outId: Int64 = 0
    DispatchQueue.main.sync {
        if NSApp == nil { _ = NSApplication.shared }

        let cfg = WKWebViewConfiguration()
        cfg.websiteDataStore = .nonPersistent()
        let web = WKWebView(frame: .zero, configuration: cfg)
        if let u = targetURL { web.load(URLRequest(url: u)) }

        let win = makeWindow(with: web, title: "WebView")
        let id = nextId; nextId += 1
        let ctrl = Controller(id: id, window: win, web: web)
        ctrls[id] = ctrl

        NotificationCenter.default.addObserver(forName: NSWindow.willCloseNotification, object: win, queue: nil) { _ in
            let reason = ctrl.closeReason
            stopAndDispose(id: id)

            if let cb = gOnClosed {
                DispatchQueue.global(qos: .userInitiated).async {
                    cb(id, reason)
                }
            }
        }

        win.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)

        startCookiePolling(for: ctrl, targetURL: targetURL, intervalMs: intervalMs)
        outId = id
    }
    return outId
}

@_cdecl("wkb_close_window")
public func wkb_close_window(_ id: Int64) {
    DispatchQueue.main.async {
        if let c = ctrls[id] {
            c.closeReason = 1 // API call
            c.window.performClose(nil)
        }
    }
}

@_cdecl("wkb_clear_persistent_storage")
public func wkb_clear_persistent_storage() {
    let types = WKWebsiteDataStore.allWebsiteDataTypes()
    WKWebsiteDataStore.default().removeData(ofTypes: types, modifiedSince: .distantPast, completionHandler: {})
}

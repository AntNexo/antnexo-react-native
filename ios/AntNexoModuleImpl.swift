import Foundation

/// The TurboModule's logic: maps arguments and calls the vendored Swift SDK (`AntNexo`, same module). `AntNexo.mm`
/// forwards to it. JS validated the values already; anything unknown is left out here rather than thrown.
@objc(AntNexoModuleImpl)
public final class AntNexoModuleImpl: NSObject {
    /// Sends an event to JS (`NativeEvent`). Main thread only.
    @objc public var emit: (([String: Any]) -> Void)?

    /// The module events go to: the newest one (a JS reload makes a new module before the old one is invalidated).
    @MainActor private static weak var current: AntNexoModuleImpl?

    // Every call reaches the SDK through `Task { @MainActor in }` from RN's module queue, in call order.

    @objc public override init() {
        super.init()
        Task { @MainActor [weak self] in
            Self.current = self
            AntNexo.onEvent = { e in Self.current?.emit?(Self.eventMap(e)) }
        }
    }

    @objc public func setWrapper(_ sdk: String) {
        Task { @MainActor in AntNexo.setWrapper(platform: .reactNative, sdk: sdk) }
    }

    @objc public func configure(_ host: String, workspace: String, options: [String: Any],
                                resolve: @escaping (Any?) -> Void,
                                reject: @escaping (String, String, [String: Any]?) -> Void) {
        let o = Self.feedbackOptions(options)
        Task { @MainActor in
            do {
                guard let url = URL(string: host) else { throw AntNexoError.invalidHost }
                try AntNexo.configure(host: url, workspace: workspace, options: o)
                resolve(nil)
            } catch {
                let (code, info) = Self.errorCode(error)
                reject(code, "AntNexo: \(code)", info)
            }
        }
    }

    /// Resolves when the sheet is gone: closed, or it couldn't open. The token is for this opening only.
    @objc public func show(_ tab: String?, screenshot: Bool, token: String?, resolve: @escaping (Any?) -> Void) {
        Task { @MainActor in
            if let token, !token.isEmpty {
                AntNexo.identify { token }
            } else {
                AntNexo.identify(nil)
            }
            await AntNexo.present(from: nil, tab: tab.flatMap(FeedbackTab.init(rawValue:)), screenshot: screenshot,
                                  onClose: { resolve(nil) })
            // prepare() has read the token: drop it, so no later opening (native or a SwiftUI modifier) reuses it
            AntNexo.identify(nil)
        }
    }

    @objc public func close() {
        Task { @MainActor in AntNexo.close() }
    }

    @objc public func unreadCount(_ resolve: @escaping (Any?) -> Void,
                                  reject: @escaping (String, String, [String: Any]?) -> Void) {
        Task { @MainActor in
            do {
                resolve(try await AntNexo.unreadCount())
            } catch {
                let (code, info) = Self.errorCode(error)
                reject(code, "AntNexo: \(code)", info)
            }
        }
    }

    @objc public func logout(_ resolve: @escaping (Any?) -> Void) {
        Task { @MainActor in
            AntNexo.identify(nil)
            await AntNexo.logout()
            resolve(nil)
        }
    }

    /// The JS bundle reloaded or the app is going away: no events into a dead context. An open sheet stays usable.
    /// Only the current module stops events; an old one invalidated late leaves the new one's alone.
    @objc public func invalidate() {
        Task { @MainActor in
            self.emit = nil
            if Self.current === self { Self.current = nil }
        }
    }

    static func feedbackOptions(_ m: [String: Any]) -> FeedbackOptions {
        func str(_ k: String) -> String? { m[k] as? String }
        func list(_ k: String) -> [String]? { (m[k] as? [Any])?.compactMap { $0 as? String } }
        return FeedbackOptions(
            tab: str("tab").flatMap(FeedbackTab.init(rawValue:)),
            tabs: list("tabs")?.compactMap(FeedbackTab.init(rawValue:)),
            boards: list("boards"),
            theme: str("theme").flatMap(FeedbackTheme.init(rawValue:)),
            color: str("color"),
            prompts: m["prompts"] as? Bool,
            whatsNew: str("whatsNew").flatMap(WhatsNewMode.init(rawValue:)),
            language: str("language"))
    }

    static func eventMap(_ e: AntNexoEvent) -> [String: Any] {
        switch e {
        case .posted(let id): return ["type": "posted", "id": id]
        case .closed: return ["type": "closed"]
        case .unread(let count): return ["type": "unread", "count": count]
        case .signedIn(let signedIn): return ["type": "signed_in", "signedIn": signedIn]
        case .identifyFailed(let code): return ["type": "identify_failed", "code": code]
        }
    }

    /// The rejection's code (`AntNexoErrorCode` in JS) and, for http, `userInfo.status`. Never the arguments.
    static func errorCode(_ error: Error) -> (String, [String: Any]?) {
        switch error as? AntNexoError {
        case .invalidHost: return ("invalid_host", nil)
        case .invalidWorkspace: return ("invalid_workspace", nil)
        case .notConfigured: return ("not_configured", nil)
        case .http(let status): return ("http", ["status": status])
        case .badResponse: return ("bad_response", nil)
        case nil: return ("network", nil)
        }
    }
}

import Foundation

/// The TurboModule's logic: maps arguments and calls the vendored Swift SDK (`AntNexo`, same module). `AntNexo.mm`
/// forwards to it. JS validated the values already; anything unknown is left out here rather than thrown.
@objc(AntNexoModuleImpl)
public final class AntNexoModuleImpl: NSObject {
    /// Sends an event to JS (`NativeEvent`).
    @objc public var emit: (([String: Any]) -> Void)?

    @objc public override init() {
        super.init()
        DispatchQueue.main.async { [weak self] in
            AntNexo.onEvent = { e in self?.emit?(Self.eventMap(e)) }
        }
    }

    @objc public func setWrapper(_ sdk: String) {
        DispatchQueue.main.async { AntNexo.setWrapper(platform: .reactNative, sdk: sdk) }
    }

    @objc public func configure(_ host: String, workspace: String, options: [String: Any],
                                resolve: @escaping (Any?) -> Void,
                                reject: @escaping (String, String, [String: Any]?) -> Void) {
        let o = Self.feedbackOptions(options)
        DispatchQueue.main.async {
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

    /// Resolves when the sheet is gone: closed, or it couldn't open.
    @objc public func show(_ tab: String?, screenshot: Bool, token: String?, resolve: @escaping (Any?) -> Void) {
        Task { @MainActor in
            // this opening's token only: the next show() sets its own (or none)
            if let token, !token.isEmpty {
                AntNexo.identify { token }
            } else {
                AntNexo.identify(nil)
            }
            await AntNexo.present(from: nil, tab: tab.flatMap(FeedbackTab.init(rawValue:)), screenshot: screenshot,
                                  onClose: { resolve(nil) })
        }
    }

    @objc public func close() {
        DispatchQueue.main.async { AntNexo.close() }
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
            await AntNexo.logout()
            resolve(nil)
        }
    }

    /// The JS bundle reloaded or the app is going away: no events into a dead context. An open sheet stays usable.
    @objc public func invalidate() {
        emit = nil
        DispatchQueue.main.async { AntNexo.onEvent = nil }
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

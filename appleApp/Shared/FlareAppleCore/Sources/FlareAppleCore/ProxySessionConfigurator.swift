import Foundation
import KotlinSharedUI
import Network

public final class ProxySessionConfigurator: NSObject, AppleProxySessionConfigurator, @unchecked Sendable {
    public static func install() {
        AppleProxyTransport.shared.configurator = ProxySessionConfigurator()
    }

    public nonisolated func configure(configuration: URLSessionConfiguration, connection: ProxyConnection) {
        configuration.urlCredentialStorage = nil
        if connection.mode == .direct {
            configuration.connectionProxyDictionary = [
                "HTTPEnable": 0, "HTTPSEnable": 0, "SOCKSEnable": 0,
                "ProxyAutoConfigEnable": 0, "ProxyAutoDiscoveryEnable": 0,
                "ExceptionsList": ["*"], "ExcludeSimpleHostnames": 1,
            ]
            return
        }
        if connection.mode == .system { return }
        guard connection.mode == .proxy,
              let host = connection.host, let port = connection.port,
              (1...65535).contains(port.intValue),
              let endpointPort = NWEndpoint.Port(rawValue: UInt16(port.intValue)) else {
            preconditionFailure("Invalid resolved proxy configuration")
        }
        let endpoint = NWEndpoint.hostPort(host: NWEndpoint.Host(host), port: endpointPort)
        var proxy: Network.ProxyConfiguration
        switch connection.protocol {
        case .https:
            proxy = Network.ProxyConfiguration(httpCONNECTProxy: endpoint, tlsOptions: NWProtocolTLS.Options())
        case .socks5:
            proxy = Network.ProxyConfiguration(socksv5Proxy: endpoint)
        default:
            preconditionFailure("Unsupported proxy protocol")
        }
        proxy.allowFailover = false
        configuration.proxyConfigurations = [proxy]
    }
}

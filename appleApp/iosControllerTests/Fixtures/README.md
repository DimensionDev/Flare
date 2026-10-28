`localhost.p12` is a public, test-only TLS identity using the existing certificate and key in `shared/src/jvmTest/resources/proxy/`. Its password is `flare-test`.

Regenerate from the repository root:

```sh
openssl pkcs12 -export -in shared/src/jvmTest/resources/proxy/localhost-cert.pem -inkey shared/src/jvmTest/resources/proxy/localhost-key.pem -out appleApp/iosControllerTests/Fixtures/localhost.p12 -passout pass:flare-test -keypbe PBE-SHA1-3DES -certpbe PBE-SHA1-3DES -macalg sha1
```

Before running `WebViewProxyTests`, boot a dedicated simulator and trust the certificate with `xcrun simctl keychain <simulator-id> add-root-cert shared/src/jvmTest/resources/proxy/localhost-cert.pem`, as the iOS workflow does.

# Example Feed plugin template

This is a GitHub repository template for a token-based Flare API v1 adapter.
The website API below is an **illustrative contract**, not an available service.
Adapt the paths, authentication, and mapping to your website before publishing.
No demonstration server is included. Tests in Flare exercise this template with
fixture HTTP responses through the real QuickJS runtime.

## Adapt the template

1. Copy this directory's contents to your own repository root.
2. Change `manifest.id`, `platform.id`, names, and description. Keep IDs stable
   after publication. Replace the one-pixel placeholder PNG with your own icon.
3. Adapt `plugin.js` using the Flare Plugin skill and your website's API evidence.
   It currently verifies an imported bearer token with `/api/me`, then reads
   `/api/posts`. It registers no detector, so choose the platform explicitly.
4. Update this README with the actual website, token acquisition steps, supported
   features, verification results, and remaining limitations.

The template requests no media permission and emits text-only posts. Every post
uses `detailType: "link"`, opening its HTTPS website URL. There is no arbitrary
profile lookup, internal post detail, write capability, or guest access. The
authenticated profile returned at login supplies the saved account identity.

## Illustrative website API to replace

The selected origin is an absolute HTTPS origin such as `https://api.example.org`
with no path. Both requests use a structured bearer token. The imported token
is accepted only after the profile request succeeds.

`GET /api/me`:

```json
{ "id": "alice", "username": "alice", "name": "Alice" }
```

`GET /api/posts?limit=20` and subsequent `&cursor=<encoded opaque cursor>`:

```json
{
  "items": [{
    "id": "post-1",
    "author": { "id": "alice", "username": "alice", "name": "Alice" },
    "created_at": "2026-01-01T00:00:00Z",
    "text": "A post from the website",
    "url": "https://example.org/posts/post-1"
  }],
  "next_cursor": "page-2"
}
```

`next_cursor` is omitted or null at the end. Empty pages may still contain a next
cursor. IDs are strings and stable across requests; request cursors never become
entity IDs. A refresh starts without the old cursor. Adapt these assumptions to
the actual API, including token expiry and rate-limit responses. The helper maps
401/404/429 to typed errors, treats other HTTP failures as `Remote`, and parses
the response's string `body` explicitly.

## Publish and install after adaptation

- Commit `manifest.json`, the self-contained `plugin.js`, `assets/icon.png`, and
  any `locales/*.json` at the root of a public GitHub repository.
- Publish a stable tag matching the manifest version, initially `v0.1.0`.
- In Flare plugin management, install `https://github.com/OWNER/REPO`, confirm the
  review, and restart Flare. A GitHub Release or `.fpp` asset is not required.
- Select your platform and HTTPS API origin, then import a real access token.
  Verify that the saved account, Home timeline, pagination, and link clicks work.

Publish later changes under a new version tag. Keep tokens and Cookies out of
the repository. Describe separately which checks passed with fixtures and which
passed against the actual website and Flare UI.

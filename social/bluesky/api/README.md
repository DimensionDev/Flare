# Bluesky API generation

This module replaces the pre-generated Ozone `bluesky` artifact with the APIs and
models used by Flare. Lexicons use the upstream `sh.christian.ozone:lexicons`
release from the version catalog. The generator and OAuth/runtime libraries
continue to use the `moe.tlaster.ozone` fork's SNAPSHOT versions. The generator and
Lexicon artifacts are build dependencies only.

`lexicon-roots.txt` lists the schemas directly used by API calls and record/model
code, including the refresh-session response used by the authentication plugin.
The `selectLexicons` task follows `ref` and `refs` recursively and writes complete
schema files to `build/selected-lexicons`. Missing schemas or definitions fail the
build. String `knownValues` are not model dependencies.

Ozone generates Kotlin sources under `build/generated/lexicons`, including the
matching `XrpcSerializersModule`. Unknown union members and enum values retain the
same fallback behavior as the original SDK. Generated sources are not committed.
The module explicitly uses the application's client and serialization versions.

To add an API or a record type, add its NSID to `lexicon-roots.txt` and run:

```sh
./gradlew :social:bluesky:api:generateLexicons :social:bluesky:jvmTest
```

Update the upstream Lexicon version when adding schemas unavailable in the current
artifact. A changed subset can alter generated names for formerly ambiguous API
methods; adjust callers when necessary.

Keep this module as an `implementation` dependency of `:social:bluesky` and out of
the Apple framework's export list, so unused generated declarations can be removed.
OAuth and the Ozone runtime libraries remain separate dependencies.

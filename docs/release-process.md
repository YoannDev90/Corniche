# Release process

Releases are cut from a **tag** — there is no version file to bump.

## Version scheme

- `versionName` is the tag without its `v` (`v1.2.3` → `1.2.3`).
- `versionCode` is `major * 1_000_000 + minor * 1_000 + patch` (`v1.2.3` →
  `1002003`) — the layout Android expects: each component must stay below
  1000.

Local builds derive the same scheme from `git describe --tags --long`: a
`v1.2.0-46-gf8af045` tree reports `1002046` / `1.2.0-dev.46+gf8af045`, so a
local release installs over a CI build of the same tag and still tells itself
apart; exactly zero commits on top of a tag report the plain `1.2.3` / base
code. No git or no `vX.Y.Z` tag falls back to `1` / `1.0`. Both values are
overridable with `-PversionCode=` / `-PversionName=` (the release workflow
does exactly that), and a malformed `versionCode` fails the build instead of
quietly shipping `1`.

## Cutting a release

1. Make sure `master` is green: `mise run check` (what CI runs).
2. Tag and push: `git tag v1.2.3 && git push origin v1.2.3`.
3. The **Release** workflow (`.github/workflows/release.yml`) then:
   - decodes the keystore from the `KEYSTORE_BASE64`,
     `KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_ALIAS` and `KEYSTORE_KEY_PASSWORD`
     repository secrets,
   - runs the unit tests and Android lint,
   - builds the signed release APK with the tag-derived version,
   - publishes the GitHub release with the APK attached. The notes are
     generated from the commits since the previous tag (`gh release create
     --generate-notes`) — the project keeps no hand-written CHANGELOG.
4. Install the published APK over the previous release as a smoke test; the
   tag-derived `versionCode` always increases.

## Notes

- The workflow runs with `cache-disabled: true` and SHA-pinned actions (see
  the zizmor hardening comments in the workflow): a tag-triggered publish must
  not read a Gradle cache an untrusted ref could have poisoned.
- Signing only exists where `keystore.properties` is present. Locally,
  `mise run build` without it produces an *unsigned* release APK — see
  [Build & signing](building.md).

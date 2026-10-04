# macOS 1.1.0 acceptance — 2026-10-04

## Published package: 1.1.0 (84)

[RIMES v1.1.0](https://github.com/scholay/rimes/releases/tag/v1.1.0) was published
as a stable Latest release on 2026-10-04 at 11:53:31 UTC. All four public assets
were downloaded without an account token and matched their staged bytes,
GitHub digests and sizes.

- Source and unchanged tag: `3fa9e40c9cd3eb74ae9cfb44559ff14f0d5d3eff`.
- CI build and packaged runtime smokes passed in
  [run 37197703815](https://github.com/scholay/rimes/actions/runs/37197703815).
  The immutable input artifact was `11302636041`, named
  `rimes-release-inputs-37197703815-1`, with SHA-256
  `a66769f56610e0b63ec80f7e90f6d4fead7e4f71adc9a04db53667a850c1a167`.
  Its source/version context, archive digest, strict extraction, ad-hoc signatures,
  bundle structure and arm64/x86_64 architectures were verified before signing.
- Developer ID Application and Installer signatures use team `585J2TL9U6`.
  The final PKG passed Apple notarization
  `e563c1de-6589-464a-a397-ba4735558874`, stapling and Gatekeeper checks.
  Temporary signing material was removed before publication.
- Published `RIMES-1.1.0.pkg`: 122,036,753 bytes, SHA-256
  `1fdc88d1725327aa29b0fe9ef1cc7c087fccdcb76ccf28d81564ddb0d671d415`.
- That exact package passed passive verification and actual macOS Installer
  installation on macOS 27.0 / Apple Silicon. The installed bundle reports
  1.1.0 / 84; all 227 payload entries match the verified signed application.
  The PackageKit receipt and registered/enabled input-source smoke passed.
- The other public assets are `BUILD-INFO.json`, `RELEASE-NOTES.md` and
  `SHA256SUMS`. Public download bytes match the same-package acceptance above;
  no published file was rebuilt or re-signed.

### One-time maintainer-approved signing exception

Documentation PR #50 was merged while the tagged build was running, advancing
`main` to `24e09cc6aa34d5c98c1d663497a191eecd2263e3`. Although the difference was
exactly six documentation files, the signing job requires the current `main`
commit to equal the tag source. The waiting signing environment was therefore
not approved; that workflow was cancelled. Its CI signing, immutable signed-stage
and protected publication jobs did not run.

After the concrete replacement package passed signing, notarization and
same-package installation, the maintainer explicitly approved a one-time
exception: sign the verified CI-built application locally without recompilation,
then publish those exact verified bytes. This exception applies only to this
1.1.0 package. It did not move the tag, rewrite `main` or weaken repository
protections. The default protected CI signing/publication policy is unchanged;
future release documentation must merge after public delivery completes.

Foreground TextEdit/IMK typing and physical Intel runtime acceptance are not
claimed for this package. The local preflight evidence below has a separate scope.

## Local preflight package

These earlier checks cover only the private local preflight package from
`f9ec959`; it was not published as `v1.1.0`. The public package and its separate
same-package installation are recorded above.

- Universal arm64/x86_64 Release builds, native Swift build system; product source f9ec959.
- Shared tests, macOS runtime smoke suite, settings-page previews and plugin catalog/import tests passed.
- Packaged application passed plugin platform/distribution, Capsule, Mailbox, Music, engine, lexicon bridge, chord mapping and Ziranma smokes while development resource bundles were temporarily unavailable. Resource lookup therefore succeeds from the installed application itself.
- Ziranma validation distinguishes full-Pinyin segmentation (`tu an` / 图案) from the single `tuan` reading (团), and validates both; it does not incorrectly require the two candidate rankings to match.
- Application and PKG signed with Developer ID for team 585J2TL9U6, independently notarized and stapled. Gatekeeper accepted the installer.
- The real PKG installed successfully to `/Library/Input Methods/RIMES.app`. Every packaged file is byte-identical to the verified signed payload; package receipt is 1.1.0 and the running IME process uses that installed path. The old per-user application was retired.
- RIMES parent and Chinese child input sources are enabled. The current source observed after installation was ABC; selection and a real-host input round trip are separate from package installation.
- Before upgrading, the prior application, user dictionaries and application data were copied to a private local backup. No user directory was reseeded.

Apple Silicon installation was checked locally. The Intel binary was built and its architecture verified; physical Intel runtime acceptance was not performed.

# macOS 1.1.0 acceptance — 2026-10-04

- Universal arm64/x86_64 Release builds, native Swift build system; product source f9ec959.
- Shared tests, macOS runtime smoke suite, settings-page previews and plugin catalog/import tests passed.
- Packaged application passed plugin platform/distribution, Capsule, Mailbox, Music, engine, lexicon bridge, chord mapping and Ziranma smokes while development resource bundles were temporarily unavailable. Resource lookup therefore succeeds from the installed application itself.
- Ziranma validation distinguishes full-Pinyin segmentation (`tu an` / 图案) from the single `tuan` reading (团), and validates both; it does not incorrectly require the two candidate rankings to match.
- Application and PKG signed with Developer ID for team 585J2TL9U6, independently notarized and stapled. Gatekeeper accepted the installer.
- The real PKG installed successfully to `/Library/Input Methods/RIMES.app`. Every packaged file is byte-identical to the verified signed payload; package receipt is 1.1.0 and the running IME process uses that installed path. The old per-user application was retired.
- RIMES parent and Chinese child input sources are enabled. The current source observed after installation was ABC; selection and a real-host input round trip are separate from package installation.
- Before upgrading, the prior application, user dictionaries and application data were copied to a private local backup. No user directory was reseeded.

Apple Silicon installation was checked locally. The Intel binary was built and its architecture verified; physical Intel runtime acceptance was not performed.

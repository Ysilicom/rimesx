# macOS permission and Glass UI validation — 2026-10-05

Base: `d4bf1a3`, matching the installed Liquid Glass build when this work began. Work is isolated on `codex/permissions-glass-ui`.

## Changes

- Permission guide and permission settings show one short instruction; expanded recovery replaces that instruction with one app-location hint. Installer pages each have one short explanation.
- The primary permission action follows request, settings, retry and explicit continuation states. Actual denial takes precedence over an optimistic preflight. Returning from System Settings never starts capture automatically.
- Buffer's custom buttons have exact 22 × 22 point frames. Capsule tabs use a constrained scroll document and keep the selected tab visible after resizing.

## Verification

- Xcode Swift 6.4 / macOS SDK 27 debug build passed. The system Command Line Tools SDK 15.1 lacks NSGlassEffectView; Homebrew Swift 6.3 cannot consume SDK 27. Use Xcode's toolchain explicitly for this branch.
- Passed: capture-permission-smoke, buffer-window-smoke in Glass, capsule-rail-smoke in Glass (including 350 → 170 → 350 point resizing and light/dark appearance), capsule-window-smoke, theme-appkit-smoke, capture-smoke, git diff --check.
- Native view renders: Glass Buffer translation at 600 points, Capsule rail, permissions settings, collapsed and expanded permission guide. The guide renderer rejects clipped buttons.
- A separate app identity with fixture data was used to inspect the actual Capsule window and click between tabs, and to expand the inert permission guide. Both retained visible controls and legible text.
- Buffer validation uses native view rendering and frame/hit-test assertions; this is not a real-host text delivery test. Permission tests use fakes and inert previews, not a fresh macOS account or a reset of the user's TCC grants.

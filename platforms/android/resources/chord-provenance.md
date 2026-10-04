# Android touch chord resources

`chord-profile.json` is an exact Android-owned copy of the iOS built-in profile,
`Shared/Sources/RimesCore/Resources/flyyao.json`, inspected October 2, 2026.
Its SHA-256 is `6cc5de9522068a57fdaecf2aa1c2272d46136ff82337ab1ad488957c72b90f0f`.
It contains 427 mappings under the repository's MIT license.

`ChordData.java` embeds the same keys, Pinyin outputs and mapping kinds. Its
complete-syllable set and `ChordProfile` encoding rules derive from the current
iOS `ChordEncoding.swift` implementation. Like iOS `Settings.keyboardChord`,
the default Android touch chord converts these Pinyin mappings to Natural Code
and routes them through the already compiled `rimes_ziranma` schema. It requires
no new native library, Lua extension or dictionary compilation.

`chord-natural-code.tsv` is a conformance fixture produced by compiling and
running that iOS `ChordEncoding.swift` against the copied profile. The JVM test
compares all 427 Java-encoded outputs to those independently obtained iOS codes.
The fixture SHA-256 is
`57a5fd7e3ab958372b62e854eac2a7be27a29e6d176d70e6e0260c0b0afc88b3`;
the inspected encoding source SHA-256 is
`b7d16dbd81ed70ea4c005afa62a38f427e97aa7988c5e77b50e17885ecfad0b1`.

The surface and gesture follow iOS `KeyboardGeometry.swift`, `KeySurface.swift`
and `Chord.swift`: one finger per hand, immutable start and movable endpoint,
released hands frozen, and one resolution after the final finger releases.
Interrupted gestures retire their pointer identities and never submit text.
An extra Android pointer on a blank area or utility during a chord also cancels
that batch and stays tracked until release. This prevents an invalid additional
touch from being silently ignored while a valid chord is delivered.

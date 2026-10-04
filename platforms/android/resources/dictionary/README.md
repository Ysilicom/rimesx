# Pinned offline Chinese–English dictionary

The Android local translation entry performs dictionary word/phrase lookup with
CC-CEDICT, maintained by the CC-CEDICT contributors and published by MDBG. It
does not provide contextual or neural sentence translation. No network request,
phone-side dictionary compilation, input history or user vocabulary is involved.

Source: <https://www.mdbg.net/chinese/dictionary?page=cedict>. The pinned archive
was downloaded directly from the official download link. Its own header records
the `2026-10-01T23:26:37Z` release and 125,166 entries. Both the original archive
and the derived index data remain under **CC BY-SA 4.0**, independently of the
application code's license. The original source header, attribution, modification
notice and complete Creative Commons license are bundled in the APK. See
`cedict.lock.json` for source, asset and license hashes and exact index metrics.

Reproduce without any network access:

```sh
python3 platforms/android/resources/dictionary/verify-dictionary.py --write
python3 platforms/android/resources/dictionary/verify-dictionary.py --check
python3 platforms/android/resources/dictionary/verify-dictionary.py --check --apk platforms/android/app/build/outputs/apk/debug/app-debug.apk
```

The converter validates the complete pinned source, chooses an ordinary lexical
gloss for each Chinese form, resolves supported variant/see references and builds
reverse aliases from English glosses. Parenthetical explanations, classifiers,
control annotations and unsupported placeholder phrases are excluded from
reverse lookup. Reverse entries are ambiguous: the deterministic selection
prefers concise lowercase-pinyin headwords and then source order; it is not a
frequency model or a context-dependent sense selector. Derived data changes are
documented in `CC-CEDICT-NOTICE.txt` and remain CC BY-SA 4.0.

`preferred-headwords.json` fixes 30 common English senses and two Chinese greeting
forms to existing CC-CEDICT definitions (for example, `I → 我`, `thank you → 谢谢`
and `谢谢 → thank you`). The converter verifies every preferred pair against the
official source before overriding an ambiguous reverse choice. This small sense
preference list is separate from the full dictionary; it neither fabricates
definitions nor adds sentence-generation rules.

The packaged `cedict-index.rmdict` contains gzip bytes; its neutral extension
avoids AAPT's implicit decompression/renaming of `.gz` assets. `--apk` checks the
final ZIP paths and exact packaged dictionary/license/notice bytes.

The `RMDICT03` index has two sorted UTF-16 radix tries and UTF-8 value
pools. It is generated on the build machine. `OfflineDictionary` loads it lazily
on a worker and shares only immutable dictionary arrays. Individual gloss strings
are decoded when matched, rather than constructing hundreds of thousands of
Java string/map objects on a phone. Source and output are bounded to 16,384 UTF-16
units; loading and lookup observe cooperative cancellation. No user source or
translated result is cached.

`auto` chooses Chinese to English when any Han code point is present; otherwise
it chooses English to Chinese. Explicit `zh-en` and `en-zh` are available. Chinese
lookup covers the dictionary's simplified and traditional spellings and greedily
chooses the longest phrase. English lookup is case-insensitive for ASCII letters,
supports exact multiword phrases and never substitutes a shorter match inside an
unknown identifier. Matched adjacent Chinese glosses receive separating spaces.
Unmatched words and supplementary code points remain exactly as typed; original
punctuation and whitespace outside matched phrases are retained. Stand-alone
numbers, spaces and punctuation do not count as unmatched segments. Coverage
counts are lexical segments, not a sentence-quality score. Empty or unmatched-only
input does not count as a translated result.

Runtime data are approximately the uncompressed index size recorded in the lock
plus small array/object headers and per-call strings. First-load time and process
memory must be measured on the target device; desktop conversion/loading timing
does not establish Android UI latency or dictionary translation quality.

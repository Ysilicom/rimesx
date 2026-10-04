#!/usr/bin/env python3
"""Reproduce and verify the pinned CC-CEDICT offline index; never downloads."""
import argparse
import gzip
import hashlib
import io
import json
import pathlib
import re
import struct
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent
ANDROID = ROOT.parents[1]
LOCK = ROOT / 'cedict.lock.json'
SOURCE = ROOT / 'cedict_1_0_ts_utf-8_mdbg.txt.gz'
ASSET = ANDROID / 'app/src/main/assets/dictionary/cedict-index.rmdict'
LICENSE = ROOT / 'CC-BY-SA-4.0.txt'
PREFERENCES = ROOT / 'preferred-headwords.json'
PACKAGED_LICENSE = ANDROID / 'app/src/main/assets/licenses/CC-CEDICT-CC-BY-SA-4.0.txt'
NOTICE = ANDROID / 'app/src/main/assets/dictionary/CC-CEDICT-NOTICE.txt'
ENTRY = re.compile(r'^(\S+) (\S+) \[(.*?)\] /(.*)/$')
REFERENCE = re.compile(r'(?:variant of|see) ([^\s\[]+)\[')
ENGLISH = re.compile(r"[A-Za-z]+(?:['-][A-Za-z]+)*(?: [A-Za-z]+(?:['-][A-Za-z]+)*)*")
NONLEXICAL = re.compile(r'^(?:CL:|see |variant of |old variant of |archaic variant of |same as |also written |also pr\. |also pr |Taiwan pr\. |surname |a surname |abbr\. for |abbreviation for |used in |one of the |Kangxi radical |radical )', re.I)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def without_parentheses(text):
    depth, result = 0, []
    for char in text:
        if char == '(':
            depth += 1
        elif char == ')' and depth:
            depth -= 1
        elif not depth:
            result.append(char)
    return ' '.join(''.join(result).split())


def glosses(definitions):
    """Deterministic dictionary glosses, not contextual sentence generation."""
    for definition in definitions:
        if NONLEXICAL.match(definition):
            continue
        for part in definition.split(';'):
            part = without_parentheses(part).strip().strip(' .,!?:;')
            if part.startswith('to '):
                part = part[3:]
            if not part or NONLEXICAL.match(part):
                continue
            if any('\u3400' <= char <= '\u9fff' or ord(char) > 0xffff for char in part):
                continue
            # Pinyin references and dictionary control annotations are not glosses.
            if '[' in part or ']' in part or part.startswith('CL:'):
                continue
            yield part


def reverse_key(gloss):
    gloss = gloss.strip(' .,!?;:').lower()
    if not ENGLISH.fullmatch(gloss):
        return None
    words = gloss.split()
    if any(word in ('sb', 'sth', 'sb\'s', 'sth\'s') for word in words):
        return None
    return gloss


def compile_maps(source, preferences=None):
    text = gzip.decompress(source).decode('utf-8')
    headers, records, chinese, english, english_rank = [], [], {}, {}, {}
    preferences = preferences or {'zh_en': {}, 'en_zh': {}}
    unverified_zh = set(preferences['zh_en'])
    unverified_en = set(preferences['en_zh'])
    metadata = {}
    for number, line in enumerate(text.splitlines(), 1):
        if line.startswith('#'):
            headers.append(line)
            if line.startswith('#! ') and '=' in line:
                key, value = line[3:].split('=', 1)
                metadata[key] = value
            continue
        if not line:
            continue
        match = ENTRY.fullmatch(line)
        if match is None:
            raise ValueError(f'Unrecognized CC-CEDICT entry at line {number}')
        traditional, simplified, pinyin, raw = match.groups()
        definitions = raw.split('/')
        records.append((traditional, simplified, definitions))
        candidates = list(glosses(definitions))
        if candidates:
            for word in (simplified, traditional):
                if word in unverified_zh and preferences['zh_en'][word] in candidates:
                    unverified_zh.remove(word)
                # CC-CEDICT orders dictionary senses; prefer a lexical sense over
                # capitalized proper-name-only senses for the same written form.
                rank = (all(char.isupper() for char in pinyin if char.isalpha()),
                        not pinyin[:1].islower(), len(candidates[0]), number)
                previous = chinese.get(word)
                if previous is None or rank < previous[0]:
                    chinese[word] = (rank, candidates[0])
        for gloss in candidates:
            key = reverse_key(gloss)
            if key is None:
                continue
            if key in unverified_en and preferences['en_zh'][key] == simplified:
                unverified_en.remove(key)
            # A reverse dictionary is ambiguous. Prefer concise common-word
            # headwords, then the stable upstream order; no frequency claims.
            rank = (not pinyin[:1].islower(), abs(len(simplified) - 2),
                    len(simplified), number)
            if key not in english_rank or rank < english_rank[key]:
                english_rank[key] = rank
                english[key] = simplified
    if len(records) != int(metadata['entries']):
        raise ValueError('Entry count differs from the upstream header')
    if unverified_zh or unverified_en:
        raise ValueError('Preferred translation is absent from the official dictionary: '
                         + repr((sorted(unverified_zh), sorted(unverified_en))))
    # Resolve source variant/see references only when the target has an ordinary
    # gloss. Unsupported cross-references remain unknown at runtime.
    for _ in range(3):
        for traditional, simplified, definitions in records:
            for definition in definitions:
                reference = REFERENCE.search(definition)
                if reference is None:
                    continue
                targets = reference.group(1).split('|')
                target = next((chinese[t] for t in targets if t in chinese), None)
                if target is not None:
                    for word in (traditional, simplified):
                        chinese.setdefault(word, target)
    chinese = {key: value[1] for key, value in chinese.items()}
    chinese.update(preferences['zh_en'])
    english.update(preferences['en_zh'])
    return chinese, english, headers, metadata, len(records)


def utf16_units(text):
    data = text.encode('utf-16-be')
    return struct.unpack('>' + 'H' * (len(data) // 2), data)


def compile_trie(mapping):
    nodes, terminals = [{}], [-1]
    values = sorted(set(mapping.values()))
    value_ids = {value: index for index, value in enumerate(values)}
    for word in sorted(mapping):
        node = 0
        for char in utf16_units(word):
            next_node = nodes[node].get(char)
            if next_node is None:
                next_node = len(nodes)
                nodes[node][char] = next_node
                nodes.append({})
                terminals.append(-1)
            node = next_node
        terminals[node] = value_ids[mapping[word]]
    # Collapse nonterminal single-child chains into radix edges. This saves
    # most of the English phrase index's otherwise redundant node arrays.
    kept = [index for index, node in enumerate(nodes)
            if index == 0 or terminals[index] >= 0 or len(node) != 1]
    node_ids = {original: index for index, original in enumerate(kept)}
    first, labels, label_offsets, targets, compact_terminals = [], [], [0], [], []
    for original in kept:
        first.append(len(targets))
        compact_terminals.append(terminals[original])
        for char, target in sorted(nodes[original].items()):
            labels.append(char)
            while target not in node_ids:
                char, target = next(iter(nodes[target].items()))
                labels.append(char)
            label_offsets.append(len(labels))
            targets.append(node_ids[target])
    first.append(len(targets))
    offsets, pool = [0], bytearray()
    for value in values:
        pool += value.encode('utf-8')
        offsets.append(len(pool))
    data = io.BytesIO()
    data.write(struct.pack('>IIIII', len(kept), len(targets), len(labels), len(values), len(pool)))
    for items, kind in ((first, 'I'), (compact_terminals, 'i'), (label_offsets, 'I'),
                        (targets, 'I'), (labels, 'H'), (offsets, 'I')):
        data.write(struct.pack('>' + kind * len(items), *items))
    data.write(pool)
    return data.getvalue(), {'keys': len(mapping), 'nodes': len(kept), 'edges': len(targets),
                              'utf16_label_units': len(labels),
                              'values': len(values), 'utf8_pool_bytes': len(pool)}


def build(source, preferences):
    chinese, english, headers, metadata, count = compile_maps(source, preferences)
    zh_data, zh_metrics = compile_trie(chinese)
    en_data, en_metrics = compile_trie(english)
    raw = b'RMDICT03' + struct.pack('>II', 3, count) + zh_data + en_data
    # GzipFile fixes the OS/header bytes across Python versions; gzip.compress
    # with mtime=0 delegated its OS byte to zlib on Python 3.11/3.12.
    compressed = io.BytesIO()
    with gzip.GzipFile(filename='', fileobj=compressed, mode='wb', compresslevel=9, mtime=0) as packed_file:
        packed_file.write(raw)
    packed = compressed.getvalue()
    notice = ('CC-CEDICT offline dictionary\n\n' + '\n'.join(headers) + '\n\n'
              'Published by MDBG and the CC-CEDICT contributors.\n'
              'Source: https://www.mdbg.net/chinese/dictionary?page=cedict\n'
              'Snapshot: ' + metadata['date'] + '\n'
              'Source and derived dictionary data: CC BY-SA 4.0.\n'
              'https://creativecommons.org/licenses/by-sa/4.0/\n'
              'Modifications by RIMES: deterministic gloss selection, reverse English gloss\n'
              'aliases, verified common-word sense choices, reference resolution and\n'
              'UTF-16 radix trie/UTF-8 pool indexing.\n'
              'This is word/phrase lookup, not contextual or neural sentence translation.\n'
              'The pinned source archive and reproducible converter are distributed in\n'
              'platforms/android/resources/dictionary/. No endorsement by MDBG is implied.\n'
              'The dictionary is supplied without warranties; see the complete packaged\n'
              'CC-CEDICT-CC-BY-SA-4.0.txt license. Application code has its separate license.\n').encode('utf-8')
    metrics = {'source_entries': count, 'source_release': metadata['date'],
               'uncompressed_index_bytes': len(raw), 'zh_en': zh_metrics, 'en_zh': en_metrics,
               'verified_common_choices': {key: len(preferences[key]) for key in ('zh_en', 'en_zh')}}
    return packed, notice, metrics


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true', help='Regenerate the index and packaged notices from the locked archive')
    parser.add_argument('--check', action='store_true', help='Verify exact bytes and hashes; the default')
    parser.add_argument('--apk', type=pathlib.Path, help='Additionally verify exact dictionary/license/notice bytes inside the built APK')
    args = parser.parse_args()
    lock = json.loads(LOCK.read_text())
    source, license_data, preference_data = SOURCE.read_bytes(), LICENSE.read_bytes(), PREFERENCES.read_bytes()
    if digest(source) != lock['source']['sha256']:
        raise ValueError('Pinned source archive hash mismatch')
    if digest(license_data) != lock['license']['sha256']:
        raise ValueError('Pinned Creative Commons license hash mismatch')
    if digest(preference_data) != lock['preferences']['sha256']:
        raise ValueError('Pinned common-word choices hash mismatch')
    data, notice, metrics = build(source, json.loads(preference_data))
    outputs = ((ASSET, data, 'index'), (NOTICE, notice, 'notice'),
               (PACKAGED_LICENSE, license_data, 'packaged_license'))
    for path, content, key in outputs:
        relative = str(path.relative_to(ANDROID))
        if args.write:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
            lock[key] = {'path': relative, 'sha256': digest(content), 'bytes': len(content)}
        elif path.read_bytes() != content or digest(content) != lock[key]['sha256']:
            raise ValueError('Generated dictionary hash/reproduction mismatch: ' + relative)
    if args.write:
        lock['metrics'] = metrics
        LOCK.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + '\n')
    elif lock['metrics'] != metrics:
        raise ValueError('Index metrics differ from the lock')
    if args.apk:
        with zipfile.ZipFile(args.apk) as apk:
            names = apk.namelist()
            for path, content, key in outputs:
                asset_path = 'assets/' + str(path.relative_to(ANDROID / 'app/src/main/assets'))
                if names.count(asset_path) != 1:
                    raise ValueError('Missing/duplicate packaged dictionary resource: ' + asset_path)
                if apk.read(asset_path) != content:
                    raise ValueError('APK dictionary resource was renamed/transformed or has a different hash: ' + asset_path)
            if any(name.startswith('assets/dictionary/cedict-index.') and name != 'assets/dictionary/cedict-index.rmdict' for name in names):
                raise ValueError('Obsolete dictionary index remains packaged in the APK')
        print('PASS APK exact offline dictionary/index/license/notice asset paths and bytes')
    print(f'PASS CC-CEDICT {metrics["source_release"]}: {metrics["source_entries"]} source entries, '
          f'{metrics["zh_en"]["keys"]} Chinese keys, {metrics["en_zh"]["keys"]} English keys, '
          f'{len(data)} packaged index bytes; all hashes, complete license and reproduction verified')


if __name__ == '__main__':
    main()

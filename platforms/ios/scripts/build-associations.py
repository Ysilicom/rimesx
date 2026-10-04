#!/usr/bin/env python3
"""Derive word-continuation associations from the bundled pinyin_simp dictionary.

Output: EngineData/associations.tsv, one line per word, sorted by UTF-8 bytes:
    word<TAB>continuation<TAB>continuation...
A continuation is kept only when the full phrase is a dictionary entry and the
remainder is itself an entry or a single character (谢谢大家 → 谢谢: 大家, but
never 谢: 谢大家). Up to LIMIT continuations per word, highest weight first.
"""
from pathlib import Path
import re, sys

LIMIT = 8
MAX_CONTINUATION = 4
HAN = re.compile(r'^[㐀-鿿豈-﫿]+$')

def build(dictionary: Path) -> list[str]:
    weights: dict[str, int] = {}
    body = dictionary.read_text(encoding='utf-8').split('\n...\n', 1)[-1]
    for line in body.splitlines():
        parts = line.split('\t')
        if len(parts) < 2 or not HAN.match(parts[0]): continue
        weight = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else 0
        weights[parts[0]] = max(weights.get(parts[0], 0), weight)
    table: dict[str, dict[str, int]] = {}
    for phrase, weight in weights.items():
        for cut in range(1, len(phrase)):
            head, tail = phrase[:cut], phrase[cut:]
            if len(tail) > MAX_CONTINUATION: continue
            if not (head in weights or len(head) == 1): continue
            if not (tail in weights or len(tail) == 1): continue
            best = table.setdefault(head, {})
            best[tail] = max(best.get(tail, 0), weight)
    lines = []
    for head, tails in table.items():
        ranked = sorted(tails.items(), key=lambda item: (-item[1], item[0]))[:LIMIT]
        lines.append('\t'.join([head] + [tail for tail, _ in ranked]))
    return sorted(lines, key=lambda line: line.split('\t', 1)[0].encode('utf-8'))

if __name__ == '__main__':
    base = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / 'Resources/EngineData'
    lines = build(base / 'pinyin_simp.dict.yaml')
    (base / 'associations.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(f'Associations: {len(lines)} words')

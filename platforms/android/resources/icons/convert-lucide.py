#!/usr/bin/env python3
"""Reproduce the pinned SVG subset as native Android VectorDrawables; no downloads."""
import argparse
import hashlib
import json
import math
import pathlib
import re
import xml.etree.ElementTree as ET
from xml.sax.saxutils import quoteattr

ROOT = pathlib.Path(__file__).resolve().parent
ANDROID = ROOT.parents[1]
LOCK = ROOT / 'lucide.lock.json'
NUMBER = re.compile(r'[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?')
PARAMETERS = {'M': 2, 'L': 2, 'H': 1, 'V': 1, 'C': 6, 'S': 4, 'Q': 4, 'T': 2, 'A': 7}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def normalized_path(data):
    # SVG may join its one-character arc flags to an endpoint (e.g. "0 0022 17").
    # Android's numeric PathParser requires these flags to be separate arguments.
    position = 0
    command = None
    result = []

    def skip():
        nonlocal position
        while position < len(data) and (data[position].isspace() or data[position] == ','):
            position += 1

    while True:
        skip()
        if position == len(data):
            break
        if data[position].isalpha():
            command = data[position]
            position += 1
            if command.upper() == 'Z':
                result.append(command)
                command = None
                continue
        if command is None or command.upper() not in PARAMETERS:
            raise ValueError('Unsupported or missing SVG command in ' + data)
        values = []
        for index in range(PARAMETERS[command.upper()]):
            skip()
            if command.upper() == 'A' and index in (3, 4):
                if position >= len(data) or data[position] not in '01':
                    raise ValueError('Invalid arc flag in ' + data)
                values.append(data[position])
                position += 1
            else:
                match = NUMBER.match(data, position)
                if match is None:
                    raise ValueError('Invalid numeric argument in ' + data)
                values.append(match.group())
                position = match.end()
        result.append(command + ' ' + ' '.join(values))
        if command.upper() == 'M':
            command = 'l' if command == 'm' else 'L'
    return ' '.join(result)


def number(value):
    return format(value, '.12g')


def shape_path(node):
    tag = node.tag.rsplit('}', 1)[-1]
    if 'transform' in node.attrib:
        raise ValueError('Unexpected transform')
    if tag == 'path':
        return normalized_path(node.attrib['d'])
    if tag in ('circle', 'ellipse'):
        x, y = float(node.attrib['cx']), float(node.attrib['cy'])
        rx = float(node.attrib['r'] if tag == 'circle' else node.attrib['rx'])
        ry = float(node.attrib['r'] if tag == 'circle' else node.attrib['ry'])
        return f'M {number(x-rx)} {number(y)} A {number(rx)} {number(ry)} 0 1 0 {number(x+rx)} {number(y)} A {number(rx)} {number(ry)} 0 1 0 {number(x-rx)} {number(y)} Z'
    if tag == 'rect':
        x, y = float(node.attrib.get('x', 0)), float(node.attrib.get('y', 0))
        width, height = float(node.attrib['width']), float(node.attrib['height'])
        rx = min(float(node.attrib.get('rx', node.attrib.get('ry', 0))), width / 2)
        ry = min(float(node.attrib.get('ry', node.attrib.get('rx', 0))), height / 2)
        if not rx or not ry:
            return f'M {number(x)} {number(y)} H {number(x+width)} V {number(y+height)} H {number(x)} Z'
        return (f'M {number(x+rx)} {number(y)} H {number(x+width-rx)} '
                f'A {number(rx)} {number(ry)} 0 0 1 {number(x+width)} {number(y+ry)} V {number(y+height-ry)} '
                f'A {number(rx)} {number(ry)} 0 0 1 {number(x+width-rx)} {number(y+height)} H {number(x+rx)} '
                f'A {number(rx)} {number(ry)} 0 0 1 {number(x)} {number(y+height-ry)} V {number(y+ry)} '
                f'A {number(rx)} {number(ry)} 0 0 1 {number(x+rx)} {number(y)} Z')
    if tag == 'line':
        return normalized_path('M ' + node.attrib['x1'] + ' ' + node.attrib['y1'] + ' L ' + node.attrib['x2'] + ' ' + node.attrib['y2'])
    if tag in ('polyline', 'polygon'):
        return normalized_path('M ' + node.attrib['points'] + (' Z' if tag == 'polygon' else ''))
    raise ValueError('Unsupported SVG element: ' + tag)


def vector(icon, lock):
    data = (ROOT / icon['svg']).read_bytes()
    if digest(data) != icon['svg_sha256']:
        raise ValueError('SVG hash mismatch: ' + icon['svg'])
    svg = ET.fromstring(data)
    expected = {'viewBox': '0 0 24 24', 'fill': 'none', 'stroke': 'currentColor',
                'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round'}
    if any(svg.attrib.get(key) != value for key, value in expected.items()):
        raise ValueError('Unexpected source style: ' + icon['svg'])
    lines = [f'<!-- Lucide {icon["upstream_file"]} at {lock["commit"]}; see resources/icons/lucide.lock.json. -->',
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             '    android:width="24dp" android:height="24dp"',
             '    android:viewportWidth="24" android:viewportHeight="24">']
    for child in svg:
        if child.attrib.get('fill', 'none') != 'none':
            raise ValueError('Unexpected source fill')
        lines += ['    <path android:fillColor="@android:color/transparent" android:strokeColor="#FF000000"',
                  '        android:strokeWidth="2" android:strokeLineCap="round" android:strokeLineJoin="round"',
                  '        android:pathData=' + quoteattr(shape_path(child)) + ' />']
    lines.append('</vector>')
    return ('\n'.join(lines) + '\n').encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true', help='Regenerate XMLs, packaged license and vector hashes')
    parser.add_argument('--check', action='store_true', help='Check exact reproduction; the default')
    args = parser.parse_args()
    lock = json.loads(LOCK.read_text())
    license_data = (ROOT / lock['license_source']).read_bytes()
    if digest(license_data) != lock['license_sha256']:
        raise ValueError('License hash mismatch')
    packaged_license = ANDROID / lock['packaged_license']
    if args.write:
        packaged_license.parent.mkdir(parents=True, exist_ok=True)
        packaged_license.write_bytes(license_data)
    elif packaged_license.read_bytes() != license_data:
        raise ValueError('Packaged license differs from pinned source')
    for icon in lock['icons']:
        data = vector(icon, lock)
        output = ANDROID / icon['vector']
        if args.write:
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(data)
            icon['vector_sha256'] = digest(data)
        elif output.read_bytes() != data or digest(data) != icon['vector_sha256']:
            raise ValueError('Vector conversion/hash mismatch: ' + str(output))
    if args.write:
        LOCK.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + '\n')
    print(f'PASS {len(lock["icons"])} pinned Lucide SVG/VectorDrawable hashes and complete packaged license')


if __name__ == '__main__':
    main()

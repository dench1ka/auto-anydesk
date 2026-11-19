#!/usr/bin/env python3
"""Resolve the APK's Android framework DEX references against an API 25 class JAR.

This is a static Android symbol check, not an installer/ART or MIUI simulation.
Java/Kotlin library API compatibility is checked separately by Android Lint and tests.
"""
import argparse
from functools import lru_cache
from pathlib import Path
import struct
import zipfile
from project_config import ROOT, APK_NAME

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--android-jar', required=True, type=Path)
parser.add_argument('--apk', type=Path, default=ROOT / 'artifacts' / APK_NAME)
args = parser.parse_args()

class ClassFile:
    def __init__(self, data):
        assert data[:4] == b'\xca\xfe\xba\xbe'
        self.data, self.pos = data, 8
        pool = [None] * self.u2()
        index = 1
        while index < len(pool):
            tag = self.u1()
            if tag == 1:
                size = self.u2()
                pool[index] = data[self.pos:self.pos + size].decode('utf-8', errors='replace')
                self.pos += size
            elif tag in {7, 8, 16, 19, 20}:
                pool[index] = self.u2()
            elif tag in {3, 4, 9, 10, 11, 12, 17, 18}:
                self.pos += 4
            elif tag in {5, 6}:
                self.pos += 8
                index += 1
            elif tag == 15:
                self.pos += 3
            else:
                raise ValueError(f'Unsupported constant-pool tag {tag}')
            index += 1
        self.u2()  # access
        self.u2()  # this
        parent = self.u2()
        parents = ([pool[pool[parent]]] if parent else [])
        parents += [pool[pool[self.u2()]] for _ in range(self.u2())]
        self.parents = parents
        self.fields = self.members(pool)
        self.methods = self.members(pool)

    def u1(self):
        value = self.data[self.pos]; self.pos += 1
        return value

    def u2(self):
        value = struct.unpack_from('>H', self.data, self.pos)[0]; self.pos += 2
        return value

    def u4(self):
        value = struct.unpack_from('>I', self.data, self.pos)[0]; self.pos += 4
        return value

    def members(self, pool):
        result = set()
        for _ in range(self.u2()):
            self.u2()  # access
            result.add((pool[self.u2()], pool[self.u2()]))
            for _ in range(self.u2()):
                self.u2()
                size = self.u4()
                self.pos += size
        return result

with zipfile.ZipFile(args.android_jar) as framework, zipfile.ZipFile(args.apk) as apk:
    @lru_cache(None)
    def load(name):
        try:
            return ClassFile(framework.read(name + '.class'))
        except KeyError:
            return None

    @lru_cache(None)
    def resolves(owner, name, descriptor, field=False):
        info = load(owner)
        if info is None:
            # Android classes ultimately inherit these stable Object members.
            return owner == 'java/lang/Object' and not field and (name, descriptor) in {
                ('toString', '()Ljava/lang/String;'), ('hashCode', '()I'),
                ('equals', '(Ljava/lang/Object;)Z'), ('getClass', '()Ljava/lang/Class;')}
        if (name, descriptor) in (info.fields if field else info.methods):
            return True
        if name == '<init>':
            return False
        return any(resolves(parent, name, descriptor, field) for parent in info.parents)

    missing = set()
    type_refs, method_refs, field_refs = set(), set(), set()
    for entry in apk.namelist():
        if not entry.endswith('.dex'):
            continue
        dex = apk.read(entry)
        def u2(offset): return struct.unpack_from('<H', dex, offset)[0]
        def u4(offset): return struct.unpack_from('<I', dex, offset)[0]
        def text(offset):
            while dex[offset] & 0x80: offset += 1
            offset += 1  # ULEB128 UTF-16 length; identifiers below are ASCII.
            return dex[offset:dex.index(b'\0', offset)].decode('utf-8', errors='replace')
        strings = [text(u4(u4(60) + 4 * i)) for i in range(u4(56))]
        types = [strings[u4(u4(68) + 4 * i)] for i in range(u4(64))]
        for name in types:
            component = name.lstrip('[')
            if component.startswith('Landroid/'):
                owner = component[1:-1]
                type_refs.add(owner)
                if load(owner) is None: missing.add('type ' + owner)
        prototypes = []
        for i in range(u4(72)):
            offset = u4(76) + 12 * i
            return_type, parameters = types[u4(offset + 4)], u4(offset + 8)
            arguments = '' if not parameters else ''.join(types[u2(parameters + 4 + 2 * j)] for j in range(u4(parameters)))
            prototypes.append('(' + arguments + ')' + return_type)
        for i in range(u4(88)):
            offset = u4(92) + 8 * i
            owner = types[u2(offset)]
            if owner.startswith('Landroid/'):
                name, descriptor = strings[u4(offset + 4)], prototypes[u2(offset + 2)]
                method_refs.add((owner, name, descriptor))
                if not resolves(owner[1:-1], name, descriptor): missing.add(owner + '->' + name + descriptor)
        for i in range(u4(80)):
            offset = u4(84) + 8 * i
            owner = types[u2(offset)]
            if owner.startswith('Landroid/'):
                name, descriptor = strings[u4(offset + 4)], types[u2(offset + 2)]
                field_refs.add((owner, name, descriptor))
                if not resolves(owner[1:-1], name, descriptor, True): missing.add(owner + '->' + name + ':' + descriptor)
    print(f'APK: {args.apk.name}; Android API 25 reference JAR: {args.android_jar.name}')
    print(f'Android framework references: {len(type_refs)} types, {len(method_refs)} methods, {len(field_refs)} fields')
    for item in sorted(missing): print('MISSING:', item)
    print('Unresolved references:', len(missing))
    raise SystemExit(1 if missing else 0)

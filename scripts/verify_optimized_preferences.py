#!/usr/bin/env python3
"""Verify DataStore reflection fields in the actual optimized APK's DEX schema."""
import argparse
import re
import struct
import zipfile
from pathlib import Path

REQUIRED = {
    'androidx.datastore.preferences.PreferencesProto$PreferenceMap': {'preferences_'},
    'androidx.datastore.preferences.PreferencesProto$Value': {'value_', 'valueCase_'},
    'androidx.datastore.preferences.PreferencesProto$StringSet': {'strings_'},
}


def dex_fields(data):
    """Read defined fields, not field references that might name removed fields."""
    if not data.startswith(b'dex\n'):
        raise ValueError('Expected a standard DEX file')

    def uint(offset):
        return struct.unpack_from('<I', data, offset)[0]

    def uleb(offset):
        result = 0
        for shift in range(0, 35, 7):
            byte = data[offset]
            offset += 1
            result |= (byte & 127) << shift
            if byte < 128:
                return result, offset
        raise ValueError('Invalid DEX ULEB128')

    # The names checked here are ASCII; unrelated strings can contain MUTF-8.
    strings = []
    for index in range(uint(0x38)):
        offset = uint(uint(0x3c) + 4 * index)
        _, start = uleb(offset)
        strings.append(data[start:data.index(b'\0', start)])
    types = [strings[uint(uint(0x44) + 4 * i)] for i in range(uint(0x40))]
    field_table = uint(0x54)
    result = set()
    for index in range(uint(0x60)):
        class_def = uint(0x64) + 32 * index
        offset = uint(class_def + 24)
        if not offset:
            continue
        sizes = []
        for _ in range(4):
            size, offset = uleb(offset)
            sizes.append(size)
        # static_fields and instance_fields each restart field_idx_diff at zero.
        for count in sizes[:2]:
            field_index = 0
            for _ in range(count):
                delta, offset = uleb(offset)
                _, offset = uleb(offset)  # access flags
                field_index += delta
                owner, _, name = struct.unpack_from('<HHI', data, field_table + 8 * field_index)
                result.add((types[owner], strings[name]))
    return result


def verify(mapping, apk_directory):
    names = {name: name for name in REQUIRED}
    with Path(mapping).open() as stream:
        for line in stream:
            if not line.startswith((' ', '#')) and ' -> ' in line:
                original, renamed = line.rstrip().removesuffix(':').split(' -> ', 1)
                if original in REQUIRED:
                    names[original] = renamed
    apks = sorted(Path(apk_directory).glob('*.apk'))
    if not apks:
        raise SystemExit(f'{apk_directory}: no APKs to verify')
    for apk in apks:
        fields = set()
        with zipfile.ZipFile(apk) as archive:
            for entry in archive.namelist():
                if re.fullmatch(r'classes\d*\.dex', entry):
                    fields.update(dex_fields(archive.read(entry)))
        errors = []
        for original, required in REQUIRED.items():
            owner = ('L' + names[original].replace('.', '/') + ';').encode('ascii')
            for field in required:
                if (owner, field.encode('ascii')) not in fields:
                    actual = sorted(name.decode('ascii', errors='replace') for cls, name in fields if cls == owner)
                    errors.append(f'{original}.{field} missing; actual fields in {names[original]}: {actual}')
        if errors:
            raise SystemExit(f'{apk}: unsafe DataStore protobuf schema:\n' + '\n'.join(errors))
        print(f'PASS {apk}: all four DataStore protobuf reflection fields defined with original names')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--variant', nargs=2, action='append', required=True, metavar=('MAPPING', 'APK_DIRECTORY'))
    args = parser.parse_args()
    for mapping_file, directory in args.variant:
        verify(mapping_file, directory)

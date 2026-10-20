#!/usr/bin/env python3
"""Verify the actual optimized schema, rather than merely checking a keep rule."""
import re
import sys
from pathlib import Path

REQUIRED = {
    'androidx.datastore.preferences.PreferencesProto$PreferenceMap': {'preferences_'},
    'androidx.datastore.preferences.PreferencesProto$Value': {'value_', 'valueCase_'},
    'androidx.datastore.preferences.PreferencesProto$StringSet': {'strings_'},
}


def verify(path):
    found = {name: set() for name in REQUIRED}
    owner = None
    errors = []
    with Path(path).open() as stream:
        for line in stream:
            if line.startswith('#'):
                continue
            if line and not line[0].isspace():
                owner = line.split(' -> ', 1)[0]
            elif owner in REQUIRED and '(' not in line:
                match = re.match(r'\s+\S+ (\w+) -> (\S+)\s*$', line)
                if match and match[1] in REQUIRED[owner]:
                    found[owner].add(match[1])
                    if match[1] != match[2]:
                        errors.append(f'{owner}.{match[1]} renamed to {match[2]}')
    for name, fields in REQUIRED.items():
        for field in fields - found[name]:
            errors.append(f'{name}.{field} missing from optimized schema')
    if errors:
        raise SystemExit(f'{path}: unsafe DataStore protobuf schema:\n' + '\n'.join(errors))
    print(f'PASS {path}: all DataStore protobuf reflection fields retained')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise SystemExit('Usage: verify_optimized_preferences.py MAPPING_FILE [...]')
    for filename in sys.argv[1:]:
        verify(filename)

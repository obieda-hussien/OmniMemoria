#!/usr/bin/env python3
"""Execute production migration SQL against SQLite and check legacy data survives."""
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/omnimemoria/di/DatabaseModule.kt').read_text()
for initial in (1, 2):
    db = sqlite3.connect(':memory:')
    db.executescript("""
        CREATE TABLE sort_presets(id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL,
            sortBy TEXT NOT NULL, sortOrder TEXT NOT NULL, groupBy TEXT, isDefault INTEGER NOT NULL);
        INSERT INTO sort_presets VALUES(42, 'My preset', 'SIZE', 'DESCENDING', NULL, 1);
        CREATE TABLE favorites(photoId INTEGER PRIMARY KEY);
        INSERT INTO favorites VALUES(9001);
        CREATE TABLE trash(photoId INTEGER PRIMARY KEY);
        INSERT INTO trash VALUES(9002);
        CREATE TABLE photo_intelligence(photoId INTEGER PRIMARY KEY, rawText TEXT);
        INSERT INTO photo_intelligence VALUES(9003, 'preserved OCR');
    """)
    if initial == 2:
        db.executescript("""
            CREATE TABLE corrupted_media(id INTEGER NOT NULL PRIMARY KEY, detectedAt INTEGER NOT NULL, reason TEXT NOT NULL);
            CREATE TABLE media_integrity_checked(id INTEGER NOT NULL PRIMARY KEY, checkedAt INTEGER NOT NULL);
            INSERT INTO corrupted_media VALUES(5, 123, 'preserved');
            INSERT INTO media_integrity_checked VALUES(6, 456);
        """)
    for version in range(initial, 3):
        block = source.split(f'val MIGRATION_{version}_{version+1} =', 1)[1].split('\n    }', 1)[0]
        statements = re.findall(r'db.execSQL\("([^"\n]+)"\)', block)
        assert statements, f'Missing migration {version}'
        for sql in statements:
            db.execute(sql)
    assert db.execute('SELECT id, name, filterConfig FROM sort_presets').fetchall() == [(42, 'My preset', '')]
    assert db.execute('SELECT * FROM favorites').fetchall() == [(9001,)]
    assert db.execute('SELECT * FROM trash').fetchall() == [(9002,)]
    assert db.execute('SELECT * FROM photo_intelligence').fetchall() == [(9003, 'preserved OCR')]
    assert db.execute("PRAGMA table_info(sort_presets)").fetchall()[-1][2:5] == ('TEXT', 1, "''")
    if initial == 2:
        assert db.execute('SELECT * FROM corrupted_media').fetchall() == [(5, 123, 'preserved')]
        assert db.execute('SELECT * FROM media_integrity_checked').fetchall() == [(6, 456)]
    db.close()
print('Passed production SQLite migration SQL: 1 -> 3 and 2 -> 3; presets and library records preserved.')

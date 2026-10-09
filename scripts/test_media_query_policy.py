#!/usr/bin/env python3
"""Compile the actual Kotlin query policy, then execute its SQL against SQLite fixtures."""
import argparse
import base64
import sqlite3
import subprocess
import tempfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--kotlinc', default='kotlinc')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
model = root / 'app/src/main/java/com/omnimemoria/domain/model'
runner = r'''
import com.omnimemoria.domain.model.*
import java.util.Base64
fun main() {
    fun emit(name: String, filter: FilterConfig = FilterConfig(), sort: SortConfig = SortConfig(), favorites: Set<Long> = emptySet(), constraint: String = "1") {
        val (selection, args) = MediaQuerySql.selection(filter)
        val sql = "SELECT _id FROM files WHERE ($selection) AND ($constraint) ORDER BY ${MediaQuerySql.sort(sort, favorites)}"
        fun encode(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
        println(listOf(name, encode(sql), args.joinToString(",") { encode(it) }).joinToString("\t"))
    }
    emit("resolution", sort = SortConfig(SortBy.RESOLUTION))
    emit("favorites", sort = SortConfig(SortBy.FAVORITES_FIRST), favorites = setOf(1L, 4L))
    emit("date", FilterConfig(dateRange = 20_000L..40_000L))
    emit("empty", FilterConfig(mediaTypes = emptySet()))
    emit("gif", FilterConfig(mediaTypes = setOf(MediaType.GIF)))
    emit("raw", FilterConfig(mediaTypes = setOf(MediaType.RAW)))
    emit("megapixels", FilterConfig(minResolutionMp = 2f))
    emit("collection", constraint = "${MediaQuerySql.idsClause(setOf(1L,4L), true)} AND ${MediaQuerySql.idsClause(setOf(4L), false)}")
    emit("name", sort = SortConfig(SortBy.NAME, SortOrder.ASCENDING))
}
'''
with tempfile.TemporaryDirectory() as scratch:
    temp = Path(scratch)
    (temp / 'Runner.kt').write_text(runner)
    subprocess.run([args.kotlinc, str(model/'FilterConfig.kt'), str(model/'SortConfig.kt'), str(model/'MediaQuerySql.kt'), str(temp/'Runner.kt'), '-include-runtime', '-d', str(temp/'queries.jar')], check=True)
    output = subprocess.check_output(['java', '-jar', str(temp/'queries.jar')], text=True)
conn = sqlite3.connect(':memory:')
conn.execute('CREATE TABLE files(_id INTEGER, _display_name TEXT, _size INTEGER, mime_type TEXT, media_type INTEGER, datetaken INTEGER, date_modified INTEGER, date_added INTEGER, width INTEGER, height INTEGER, duration INTEGER)')
conn.executemany('INSERT INTO files VALUES(?,?,?,?,?,?,?,?,?,?,?)', [
    (1,'alpha',100,'image/jpeg',1,0,20,10,400,300,0),
    (2,'Alpha',100,'image/jpeg',1,15000,0,0,1000,50,0),
    (3,'video',200,'video/mp4',3,5000,0,0,4000,2000,90000),
    (4,'gif',30,'image/gif',1,0,0,40,20,20,0),
    (5,'raw',500,'image/x-adobe-dng',1,1000,0,0,6000,4000,0),
    (6,'empty',0,'image/jpeg',1,999999,0,0,0,0,0),
])
expected = {'resolution':[5,3,1,2,4], 'favorites':[4,1,2,3,5], 'date':[4,1], 'empty':[], 'gif':[4], 'raw':[5], 'megapixels':[3,5], 'collection':[1], 'name':[1,2,4,5,3]}
for line in output.splitlines():
    name, encoded, raw_args = line.split('\t')
    sql = base64.b64decode(encoded).decode()
    values = [base64.b64decode(value).decode() for value in raw_args.split(',')] if raw_args else []
    result = [row[0] for row in conn.execute(sql, values)]
    assert result == expected[name], (name, result, expected[name])
    # Real LIMIT/OFFSET queries must concatenate to exactly the globally ordered results.
    pages = [row[0] for offset in range(0,len(result),2) for row in conn.execute(sql + ' LIMIT 2 OFFSET ?', values+[offset])]
    assert pages == result, (name, pages, result)
print('Passed 9 Kotlin/SQLite policy cases and 9 pagination checks.')

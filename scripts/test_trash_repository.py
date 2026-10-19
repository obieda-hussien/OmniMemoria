#!/usr/bin/env python3
"""Execute production TrashRepository with deterministic JVM Android boundary doubles.

This verifies repository behavior, not Android's real permission dialogs or providers.
The compiler lib can be a Kotlin installation or Gradle's bundled Kotlin compiler lib.
"""
import argparse
import subprocess
import tempfile
from pathlib import Path

parser = argparse.ArgumentParser()
compiler = parser.add_mutually_exclusive_group(required=True)
compiler.add_argument('--compiler-lib')
compiler.add_argument('--compiler-classpath')
parser.add_argument('--stdlib')
parser.add_argument('--coroutines', required=True)
args = parser.parse_args()
if args.compiler_lib:
    lib = Path(args.compiler_lib)
    compiler_classpath = str(lib / '*')
    stdlib = next(p for p in lib.glob('kotlin-stdlib*.jar')
                  if not any(part in p.name for part in ('jdk', 'extensions', 'sources', 'javadoc', 'common')))
else:
    if not args.stdlib:
        parser.error('--stdlib is required with --compiler-classpath')
    compiler_classpath = args.compiler_classpath
    stdlib = Path(args.stdlib)
root = Path(__file__).resolve().parents[1]
production = root / 'app/src/main/java/com/omnimemoria'
stubs = {
    'Context.kt': '''package android.content
open class Context(val contentResolver: ContentResolver = ContentResolver()) {
 var permission = 0
 fun checkSelfPermission(name: String) = permission
}
class ContentResolver {
 val items = linkedMapOf<String, Boolean>()
 var nullQuery = false; var refuseDelete = false; var deletes = 0
 fun delete(uri: android.net.Uri, selection: String?, args: Array<String>?): Int {
  deletes++
  return if (!refuseDelete && items.remove(uri.value) != null) 1 else 0
 }
 fun query(uri: android.net.Uri, projection: Array<String>, bundle: android.os.Bundle?, signal: Any?) = read(uri)
 fun query(uri: android.net.Uri, projection: Array<String>, selection: String?, args: Array<String>?, sort: String?) = read(uri)
 private fun read(uri: android.net.Uri): android.database.Cursor? =
  if (nullQuery) null else android.database.Cursor(items.containsKey(uri.value), items[uri.value] == true)
}
object ContentUris { fun withAppendedId(uri: android.net.Uri, id: Long) = android.net.Uri(uri.value + "/" + id) }
''',
    'Cursor.kt': '''package android.database
class Cursor(val exists: Boolean, val trashed: Boolean): java.io.Closeable {
 fun moveToFirst() = exists
 fun getColumnIndexOrThrow(name: String) = 1
 fun getInt(index: Int) = if (trashed) 1 else 0
 override fun close() {}
}
''',
    'Uri.kt': '''package android.net
data class Uri(val value: String) { override fun toString() = value }
''',
    'Os.kt': '''package android.os
object Build { object VERSION { var SDK_INT = 29 } }
object Environment { var allFiles = true; fun isExternalStorageManager() = allFiles }
class Bundle { fun putInt(name: String, value: Int) {} }
''',
    'Manifest.kt': '''package android
object Manifest { object permission {
 const val READ_MEDIA_VIDEO = "video"; const val READ_MEDIA_IMAGES = "images"
 const val READ_EXTERNAL_STORAGE = "storage"
} }
''',
    'PackageManager.kt': '''package android.content.pm
object PackageManager { const val PERMISSION_GRANTED = 0 }
''',
    'PendingIntent.kt': '''package android.app
class PendingIntent
''',
    'MediaStore.kt': '''package android.provider
object MediaStore {
 var failRequest = false
 const val QUERY_ARG_MATCH_TRASHED = "trashed"; const val MATCH_INCLUDE = 1
 fun createTrashRequest(cr: android.content.ContentResolver, uris: List<android.net.Uri>, trash: Boolean): android.app.PendingIntent {
  if (failRequest) throw java.io.IOException("Request failed")
  return android.app.PendingIntent()
 }
 fun createDeleteRequest(cr: android.content.ContentResolver, uris: List<android.net.Uri>): android.app.PendingIntent {
  if (failRequest) throw java.io.IOException("Request failed")
  return android.app.PendingIntent()
 }
 object Images { object Media { val EXTERNAL_CONTENT_URI = android.net.Uri("content://media/external/images/media") } }
 object Video { object Media { val EXTERNAL_CONTENT_URI = android.net.Uri("content://media/external/video/media") } }
}
''',
    'Annotations.kt': '''package javax.inject
annotation class Inject
annotation class Singleton
''',
    'Hilt.kt': '''package dagger.hilt.android.qualifiers
annotation class ApplicationContext
''',
    'Room.kt': '''package androidx.room
annotation class Entity(val tableName: String)
annotation class PrimaryKey
''',
    'Model.kt': '''package com.omnimemoria.domain.model
data class MediaPhoto(val id: Long, val uri: android.net.Uri, val name: String, val mimeType: String)
''',
    'Dao.kt': '''package com.omnimemoria.data.local.db
import kotlinx.coroutines.flow.*
class TrashDao {
 val rows = linkedMapOf<Long, TrashItem>()
 fun getAllFlow(): Flow<List<TrashItem>> = flowOf(rows.values.toList())
 fun getCount(): Flow<Int> = flowOf(rows.size)
 fun getTotalItems(): Flow<Int> = flowOf(rows.size)
 suspend fun getById(id: Long) = rows[id]
 suspend fun upsert(item: TrashItem) { rows[item.id] = item }
 suspend fun delete(id: Long) { rows.remove(id) }
 suspend fun getAll() = rows.values.toList()
 suspend fun getExpiredItems(cutoff: Long) = rows.values.filter { it.deletedAt < cutoff }
}
''',
    'Runner.kt': '''import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.omnimemoria.data.local.db.*
import com.omnimemoria.data.repository.TrashRepository
import com.omnimemoria.domain.model.MediaPhoto
import kotlinx.coroutines.*
class Fixture(sdk: Int = 29) {
 val context = Context(); val dao = TrashDao(); val repository = TrashRepository(context, dao)
 val photo = MediaPhoto(42, Uri("content://media/external/images/media/42"), "photo.jpg", "image/jpeg")
 val item = TrashItem(42, "photo.jpg", 42, 0, "image/jpeg")
 init { Build.VERSION.SDK_INT = sdk; MediaStore.failRequest = false; Environment.allFiles = true; context.contentResolver.items[photo.uri.value] = false }
}
fun main() = runBlocking {
 var cases = 0
 suspend fun test(action: suspend () -> Unit) { action(); cases++ }
 test {
  val f = Fixture(); var done = false
  f.repository.moveToTrashWithFallback(f.photo, { _, _ -> error("Unexpected dialog") }, { done = true })
  check(done && f.context.contentResolver.deletes == 0 && f.dao.rows.size == 1)
  f.repository.confirmRestoreFromTrash(f.item)
  check(f.dao.rows.isEmpty() && f.context.contentResolver.items.containsKey(f.photo.uri.value))
 }
 test {
  val f = Fixture(); f.dao.upsert(f.item)
  f.repository.confirmPermanentlyDelete(f.item)
  check(f.dao.rows.isEmpty() && f.context.contentResolver.items.isEmpty())
 }
 test {
  val f = Fixture(); f.dao.upsert(f.item); f.context.contentResolver.refuseDelete = true
  check(runCatching { f.repository.confirmPermanentlyDelete(f.item) }.isFailure)
  check(f.dao.rows.size == 1 && f.context.contentResolver.items.size == 1)
 }
 test {
  val f = Fixture(30); MediaStore.failRequest = true; var done = false; var error = false
  f.repository.moveToTrashWithFallback(f.photo, { _, _ -> error("Unexpected dialog") }, { done = true }, { error = true })
  check(error && !done && f.dao.rows.isEmpty() && f.context.contentResolver.deletes == 0)
 }
 test {
  val f = Fixture(30); var error = false
  f.repository.moveToTrashWithFallback(f.photo, { _, confirm -> confirm() }, {}, { error = true })
  check(error && f.dao.rows.isEmpty())
 }
 test {
  val f = Fixture(30); var done = false
  f.repository.moveToTrashWithFallback(f.photo, { _, confirm -> f.context.contentResolver.items[f.photo.uri.value] = true; confirm() }, { done = true })
  check(done && f.dao.rows.size == 1)
  check(runCatching { f.repository.confirmRestoreFromTrash(f.item) }.isFailure)
  f.context.contentResolver.items[f.photo.uri.value] = false
  f.repository.confirmRestoreFromTrash(f.item)
  check(f.dao.rows.isEmpty() && f.context.contentResolver.items.size == 1)
 }
 test {
  val f = Fixture(30); f.dao.upsert(f.item); f.context.contentResolver.nullQuery = true
  check(runCatching { f.repository.confirmPermanentlyDelete(f.item) }.isFailure)
  check(f.dao.rows.size == 1)
 }
 test {
  val f = Fixture(30); f.dao.upsert(f.item)
  check(runCatching { f.repository.confirmPermanentlyDelete(f.item) }.isFailure)
  check(f.dao.rows.size == 1)
 }
 test {
  val f = Fixture(30); f.dao.upsert(f.item); f.context.contentResolver.items[f.photo.uri.value] = true
  f.repository.cleanExpired(); check(f.dao.rows.size == 1)
  f.context.contentResolver.items.clear(); f.repository.cleanExpired(); check(f.dao.rows.isEmpty())
 }
 test {
  val f = Fixture(30); var reported = false
  val failure = runCatching { f.repository.moveToTrashWithFallback(f.photo, { _, _ -> throw CancellationException() }, {}, { reported = true }) }.exceptionOrNull()
  check(failure is CancellationException && !reported && f.dao.rows.isEmpty())
 }
 test {
  val f = Fixture(30); f.dao.upsert(f.item); f.context.contentResolver.items.clear()
  Environment.allFiles = false; f.context.permission = -1
  check(runCatching { f.repository.confirmPermanentlyDelete(f.item) }.isFailure)
  check(f.dao.rows.size == 1)
 }
 println("Passed production TrashRepository: " + cases + " regression scenarios.")
}
'''
}
with tempfile.TemporaryDirectory() as scratch:
    temp = Path(scratch)
    for name, contents in stubs.items():
        (temp / name).write_text(contents)
    jar = temp / 'trash-tests.jar'
    cp = str(stdlib) + ':' + args.coroutines
    subprocess.run(['java', '-cp', compiler_classpath, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-nowarn', '-no-stdlib', '-no-reflect', '-classpath', cp,
                    *map(str, temp.glob('*.kt')),
                    str(production / 'data/repository/TrashRepository.kt'),
                    str(production / 'data/local/db/TrashItem.kt'), '-d', str(jar)], check=True)
    subprocess.run(['java', '-cp', str(jar) + ':' + cp, 'RunnerKt'], check=True)

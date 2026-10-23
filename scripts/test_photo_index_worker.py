#!/usr/bin/env python3
"""Run the production index worker with JVM boundary doubles.

Checks revision races and retry results; does not evaluate native OCR quality.
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
    'Android.kt': 'package android.content; class Context',
    'Clock.kt': 'package android.os; object SystemClock { fun elapsedRealtime() = System.nanoTime() / 1_000_000 }',
    'Hilt.kt': 'package androidx.hilt.work; annotation class HiltWorker',
    'Assisted.kt': 'package dagger.assisted; annotation class Assisted; annotation class AssistedInject',
    'Room.kt': """package androidx.room
annotation class Entity(val tableName: String)
annotation class PrimaryKey
annotation class ColumnInfo(val defaultValue: String)
suspend fun <T> com.omnimemoria.data.local.db.AppDatabase.withTransaction(block: suspend () -> T): T = block()
""",
    'Work.kt': """package androidx.work
class Data(val values: Map<String, Any?> = emptyMap()) {
 fun getLong(key: String, fallback: Long) = values[key] as? Long ?: fallback
 fun getBoolean(key: String, fallback: Boolean) = values[key] as? Boolean ?: fallback
 fun getLongArray(key: String) = values[key] as? LongArray
 fun getString(key: String) = values[key] as? String
}
fun workDataOf(vararg pairs: Pair<String, Any?>) = Data(mapOf(*pairs))
class WorkerParameters(val data: Data = Data(), val attempts: Int = 0)
abstract class CoroutineWorker(context: android.content.Context, val params: WorkerParameters) {
 val applicationContext = context
 val inputData = params.data; val runAttemptCount = params.attempts; val isStopped = false
 abstract suspend fun doWork(): Result
 suspend fun setForeground(info: Any) {}
 suspend fun setProgress(data: Data) {}
 data class Result(val state: String, val data: Data = Data()) {
  companion object {
   fun success(data: Data = Data()) = Result("success", data)
   fun failure(data: Data = Data()) = Result("failure", data)
   fun retry() = Result("retry")
  }
 }
}
""",
    'Flags.kt': """package com.omnimemoria.domain.flags
import kotlinx.coroutines.flow.MutableStateFlow
enum class FeatureFlag { OCR, SMART_FILTERS, ARABIC_OCR, ML_LABELS, VIBE_ALBUMS, FACE_DETECTION, PIXEL_PALETTE, PHOTO_DNA }
class FeatureFlagManager { val featureStates = MutableStateFlow(mapOf(FeatureFlag.OCR to true)) }
""",
    'Database.kt': """package com.omnimemoria.data.local.db
class IntelligenceDao {
 val rows = mutableMapOf<Long, PhotoIntelligence>()
 suspend fun getById(id: Long) = rows[id]
 suspend fun upsert(item: PhotoIntelligence) { rows[item.id] = item }
}
class TrashDao { suspend fun getById(id: Long): Any? = null }
class CorruptedDao { suspend fun getAllIds() = emptyList<Long>() }
class AppDatabase {
 val intelligence = IntelligenceDao()
 fun photoIntelligenceDao() = intelligence
 fun trashDao() = TrashDao()
 fun corruptedMediaDao() = CorruptedDao()
}
""",
    'Analysis.kt': """package com.omnimemoria.data.intelligence
import com.omnimemoria.data.local.db.PhotoIntelligence
import com.omnimemoria.domain.intelligence.AnalysisPolicy
data class IndexImage(val id: Long, val modified: Long, val size: Long)
class IndexImageSource {
 var images = listOf(IndexImage(42, 1, 100))
 var denied = false
 suspend fun page(after: Long, ids: List<Long>? = null): List<IndexImage> {
  if (denied) throw SecurityException()
  return images.filter { it.id > after && (ids == null || it.id in ids) }
 }
}
class ArabicModelStore { suspend fun isInstalled() = false; fun isHighAccuracyInstalled() = false }
class PaddleOcrModelStore { fun isInstalled() = false }
class PhotoAnalyzer {
 var onAnalyze: () -> Unit = {}; var released = false
 suspend fun analyze(image: IndexImage, old: PhotoIntelligence?, required: Int, arabicReady: Boolean, analysisVersion: Int): PhotoIntelligence {
  onAnalyze()
  return PhotoIntelligence(image.id, "recognized", "", false, false, false, "", "", "", indexedAt = 1, embeddingId = null,
   completedMask = required, sourceModified = image.modified, sourceSize = image.size, analysisVersion = analysisVersion)
 }
 suspend fun release() { released = true }
}
""",
    'Scheduler.kt': """package com.omnimemoria.data.worker
class WorkManagerScheduler {
 val queued = mutableListOf<Long>()
 fun scheduleBackgroundIndex(afterId: Long = 0, ids: List<Long>? = null, append: Boolean = false, userInitiated: Boolean = false) { queued += afterId }
}
internal object WorkForeground {
 fun info(context: android.content.Context, notificationId: Int, title: String, message: String): Any = Any()
}
""",
    'Runner.kt': """import android.content.Context
import androidx.work.*
import com.omnimemoria.data.intelligence.*
import com.omnimemoria.data.local.db.*
import com.omnimemoria.data.worker.*
import com.omnimemoria.domain.flags.*
import kotlinx.coroutines.runBlocking
class Fixture {
 val flags = FeatureFlagManager(); val db = AppDatabase(); val source = IndexImageSource()
 val analyzer = PhotoAnalyzer(); val scheduler = WorkManagerScheduler()
 fun worker(attempts: Int = 0) = PhotoIndexWorker(Context(), WorkerParameters(workDataOf(
  PhotoIndexWorker.WORK_INPUT_PHOTO_IDS to longArrayOf(42), PhotoIndexWorker.WORK_INPUT_USER_INITIATED to true), attempts),
  flags, db, source, analyzer, ArabicModelStore(), PaddleOcrModelStore(), scheduler)
}
fun main() = runBlocking {
 var cases = 0
 suspend fun test(action: suspend () -> Unit) { action(); cases++ }
 test {
  val f = Fixture()
  f.analyzer.onAnalyze = { f.source.images = listOf(IndexImage(42, 2, 200)) }
  check(f.worker().doWork().state == "retry")
  check(f.db.intelligence.rows.isEmpty() && f.scheduler.queued.isEmpty() && f.analyzer.released)
  f.analyzer.onAnalyze = {}
  val retry = f.worker(1).doWork()
  check(retry.state == "success" && retry.data.getString(PhotoIndexWorker.OUTPUT_STATUS) == "complete")
  check(f.db.intelligence.rows[42]?.sourceModified == 2L && f.db.intelligence.rows[42]?.sourceSize == 200L)
 }
 test {
  val f = Fixture(); f.analyzer.onAnalyze = { f.source.images = listOf(IndexImage(42, 2, 200)) }
  val result = f.worker(3).doWork()
  check(result.state == "failure" && result.data.getString(PhotoIndexWorker.OUTPUT_STATUS) == "photo_changed")
  check(f.db.intelligence.rows.isEmpty())
 }
 test {
  val f = Fixture(); f.analyzer.onAnalyze = { f.source.images = emptyList() }
  val result = f.worker().doWork()
  check(result.state == "success" && result.data.values["processed"] == 0 && f.db.intelligence.rows.isEmpty())
 }
 test {
  val f = Fixture(); f.analyzer.onAnalyze = { f.flags.featureStates.value = emptyMap() }
  val result = f.worker().doWork()
  check(result.data.getString(PhotoIndexWorker.OUTPUT_STATUS) == "preferences_changed")
  check(f.scheduler.queued == listOf(0L) && f.db.intelligence.rows.isEmpty())
 }
 test {
  val f = Fixture(); val result = f.worker().doWork()
  check(result.state == "success" && result.data.values["processed"] == 1)
  check(f.db.intelligence.rows[42]?.rawText == "recognized")
 }
 test {
  val f = Fixture(); f.source.denied = true; val result = f.worker().doWork()
  check(result.state == "failure" && result.data.getString(PhotoIndexWorker.OUTPUT_STATUS) == "storage_permission_required")
  check(f.analyzer.released)
 }
 println("Passed production PhotoIndexWorker: " + cases + " regression scenarios.")
}
"""
}
with tempfile.TemporaryDirectory() as scratch:
    temp = Path(scratch)
    for name, contents in stubs.items():
        (temp / name).write_text(contents)
    jar = temp / 'index-tests.jar'
    cp = str(stdlib) + ':' + args.coroutines
    subprocess.run(['java', '-cp', compiler_classpath, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-nowarn', '-no-stdlib', '-no-reflect', '-classpath', cp,
                    *map(str, temp.glob('*.kt')),
                    str(production / 'data/worker/PhotoIndexWorker.kt'),
                    str(production / 'data/local/db/PhotoIntelligence.kt'),
                    str(production / 'domain/intelligence/AnalysisPolicy.kt'), '-d', str(jar)], check=True)
    subprocess.run(['java', '-cp', str(jar) + ':' + cp, 'RunnerKt'], check=True)

# 📸 OmniMemoria — معرض الصور الذكي
### Android · Kotlin · Jetpack Compose · Material 3 Expressive · Android 15/16

> تطبيق معرض صور أندرويد من الجيل الجديد — يجمع بين الـ On-Device AI، وشكل M3 Expressive حقيقي، وضغط ذكي للصور والفيديوهات، وميزات مش موجودة في أي تطبيق تاني.

---

## 📑 فهرس المحتويات

1. [تحليل السوق](#-تحليل-السوق--وين-الفراغ)
2. [Material 3 Expressive](#-material-3-expressive--الجديد)
3. [Android 15/16 APIs](#-android-1516-apis-الجديدة)
4. [صيغ الصور المدعومة](#-صيغ-الصور-المدعومة)
5. [الميزات الفريدة](#-الميزات-الفريدة--حاجات-مش-موجودة-في-حتة)
6. [ضغط الصور الذكي](#-ضغط-الصور-الذكي--بدون-فقدان-الجودة)
7. [ضغط الفيديوهات](#-ضغط-الفيديوهات)
8. [سلة المهملات والمفضلة](#-سلة-المهملات-والمفضلة)
9. [الفلاتر والترتيب](#-الفلاتر-والترتيب--للصور-والمجلدات)
10. [نظام OCR والعربي](#-نظام-ocr--حل-مشكلة-العربي)
11. [نظام RAG المحلي](#-نظام-rag-المحلي--البحث-الذكي)
12. [Feature Flags](#-نظام-feature-flags--الميزات-الاختيارية)
13. [Architecture الكاملة](#-architecture-الكاملة)
14. [Tech Stack](#-tech-stack)
15. [Database Schema](#-database-schema)
16. [Background Indexing](#-background-indexing--workmanager)
17. [مقارنة المنافسين](#-مقارنة-المنافسين)
18. [خطة التنفيذ - Phases](#-خطة-التنفيذ--phases)
19. [تحذيرات تقنية مهمة](#️-تحذيرات-تقنية-مهمة-من-تجارب-الـ-tech-stack)

---

## 🔍 تحليل السوق — وين الفراغ؟

### التطبيقات الموجودة وعيوبها

| التطبيق | القوة | الضعف |
|---|---|---|
| Google Photos | AI قوي، بحث ممتاز | يحتاج cloud، يبيع بياناتك |
| F-Stop | تنظيم ممتاز | واجهة قديمة، مدفوع للميزات الكبيرة |
| PicSort | Face recognition محلي | بطيء، UI عادي جداً |
| Simple Gallery | خفيف وسريع | ذكاء صفر، شكل قديم |
| 1Gallery | Vault ممتاز | UI قديم، مفيش ذكاء |

### الفجوة الحقيقية

> **مفيش تطبيق بيجمع الـ On-Device AI + شكل M3 Expressive حقيقي + ميزات مبتكرة + OCR عربي + RAG Search في نفس الوقت.**

---

## 🎨 Material 3 Expressive — الجديد

### Components الجديدة اللي لازم تُستخدم

- **Button Groups** — بتغير الشكل والحجم بـ spring animation
- **FAB Menu** — بيستبدل الـ speed dial القديم
- **Loading Indicator** جديد — مش الـ circular القديم
- **Split Button** — زرار بنصين ووظيفتين مختلفتين
- **35 شكل shape** مختلف مع **shape morphing transitions**
  - مثال: square بتتحول لـ squircle وانت بتتنقل بين الشاشات

### نظام الـ Animation

- **Spring-based animations** — مش linear
- كل حاجة بتحس إنها حية ومرنة
- Spring physics على كل interaction: tap، scroll، expand

---

## ⚙️ Android 15/16 APIs الجديدة

| API | الوصف | الاستخدام في التطبيق |
|---|---|---|
| **Embedded Photo Picker** | دمج الـ photo picker جوا الـ view hierarchy | Onboarding + import |
| **Ultra HDR في HEIC** | دعم Ultra HDR في HEIC وAVIF | HDR Viewer |
| **READ_MEDIA_IMAGES** | Permission صح لـ Android 13+ | Gallery access |
| **Edge-to-edge** | إجباري في Android 16 | كل الشاشات |
| **Predictive Back** | animations للـ back gesture | Navigation |

---

## 🖼️ صيغ الصور المدعومة

### جدول الصيغ الكامل

| الصيغة | الدعم | الخصائص | API مطلوب |
|---|---|---|---|
| **JPEG / JPG** | ✅ قراءة + كتابة | lossy، الأكثر شيوعاً | API 1+ |
| **PNG** | ✅ قراءة + كتابة | lossless، يدعم شفافية | API 1+ |
| **WebP** | ✅ قراءة + كتابة | lossy + lossless، أصغر 26% من PNG | API 17+ |
| **HEIC / HEIF** | ✅ قراءة + كتابة | أصغر 50% من JPEG، HDR | API 28+ |
| **AVIF** | ✅ قراءة + كتابة | أفضل ضغط، HDR، Wide Color | API 31+ (stable 34+) |
| **GIF** | ✅ قراءة + عرض متحرك | Coil 3 يعرضه متحرك | API 1+ |
| **BMP** | ✅ قراءة | بدون ضغط | API 1+ |
| **WBMP** | ✅ قراءة | أبيض وأسود | API 1+ |
| **SVG** | ✅ قراءة (عرض) | رسومات vector | Coil SVG plugin |
| **RAW (DNG)** | ✅ قراءة + metadata | Camera2 RAW، professional | API 21+ |
| **CR2 / NEF / ARW** | ✅ قراءة (preview) | RAW من كاميرات Sony/Canon/Nikon | عبر LibRaw |
| **TIFF** | ✅ قراءة | professional، multi-page | عبر libtiff |
| **ICO** | ✅ قراءة | أيقونات | Coil |
| **Ultra HDR JPEG** | ✅ قراءة + كتابة | HDR gainmap مضمّن في JPEG | API 34+ |
| **Ultra HDR HEIC** | ✅ قراءة + كتابة | أفضل HDR compression | API 36+ |

### Smart Format Detection

```kotlin
object FormatDetector {
    fun detect(file: File): ImageFormat {
        val header = file.readBytes().take(12).toByteArray()
        return when {
            header.startsWith("FFD8FF")         -> ImageFormat.JPEG
            header.startsWith("89504E47")        -> ImageFormat.PNG
            header.startsWith("52494646")        -> ImageFormat.WEBP
            header.contains("ftyp")              -> detectHeifVariant(header)
            isAvif(header)                        -> ImageFormat.AVIF
            header.startsWith("47494638")        -> ImageFormat.GIF
            header.startsWith("424D")            -> ImageFormat.BMP
            isDng(file)                           -> ImageFormat.DNG_RAW
            else                                  -> ImageFormat.UNKNOWN
        }
    }

    private fun detectHeifVariant(header: ByteArray): ImageFormat =
        if (header.contains("avif") || header.contains("avis"))
            ImageFormat.AVIF
        else
            ImageFormat.HEIC
}
```

### مكتبة avif-coder للـ AVIF/HEIC

```kotlin
// Decode AVIF أو HEIC
val bitmap: Bitmap = HeifCoder().decode(byteArray)

// Encode to AVIF (أفضل compression)
val avifBytes: ByteArray = HeifCoder().encodeAvif(bitmap)

// Encode to HEIC (iOS compatible)
val heicBytes: ByteArray = HeifCoder().encodeHeic(bitmap)

// في Coil 3 — دعم تلقائي
val imageLoader = ImageLoader.Builder(context)
    .components { add(HeifDecoder.Factory(context)) }
    .build()
```

---

## 💡 الميزات الفريدة — حاجات مش موجودة في حتة

---

### 1. 🎭 `Vibe Albums` — ألبومات المزاج البصري

مش face detection عادي. ML Kit Custom Model بيحلل:

- درجات الألوان الدافئة/الباردة
- مستوى الإضاءة (golden hour / night / daylight)
- الـ aesthetic style: minimalist / busy / nature / indoor

**النتيجة:** ألبومات اسمها "صور المساء الدافي" أو "لحظات هادية" — مش بس "Beach" أو "People".

**Implementation:** ML Kit Image Labeling + Palette API + Rule Engine

---

### 2. 🗺️ `Memory Map` — خريطة الذكريات الحية

بدل الـ timeline العادية، خريطة تفاعلية بتجمع الصور بـ GPS clusters.

- Compose + Maps SDK
- بتضغط على cluster فيفتح animated bottom sheet بالصور
- بيعمل "رحلة مرئية" لكل الأماكن اللي رحتلها
- Clustering algorithm: DBSCAN على الـ GPS coordinates

---

### 3. 🧬 `Photo DNA` — كشف التشابه الذكي

**Perceptual Hashing (pHash)** — مش hash عادي:

- بيكشف الصور المتشابهة حتى لو اتعدّل عليها أو اتضغطت
- بيعرضهالك جنب بعض مع نسبة تشابه %
- زر "احتفظ بالأحسن" — بيختار أعلى جودة أوتوماتيك
- توفير مساحة التخزين

---

### 4. 🔐 `Decoy Vault` — الخزينة بـ PIN مزيف

نظام privacy حقيقي من مستويين:

- **PIN رئيسي** → يفتح كل الصور الحقيقية السرية
- **PIN مزيف** → يفتح "خزينة مزيفة" بصور عادية اخترتها

لو حد أجبرك تفتح التطبيق، بتديه الـ PIN المزيف — مش موجود في أي gallery عادي.

**Implementation:** AES-256 + Android Keystore + مسارين مستقلين في الـ Navigation

> ⚠️ هذه الميزة opt-in بـ default = false، المستخدم بيفعّلها بنفسه.

---

### 5. 🎨 `Pixel Palette` — البحث بالألوان

- كل صورة بتعمل لها color fingerprint (3-5 ألوان dominant)
- بتفتح color wheel وبتختار لون، بتلاقي كل الصور اللي فيها اللون ده
- مفيد لـ: دور على صور الغروب الأحمر، صور البحر الأزرق

**Implementation:** AndroidX Palette API + Room index على الألوان

---

### 6. 🌊 `Temporal Wave` — عرض الـ Timeline بشكل مختلف

بدل الـ grid العادي:

- عرض "wave" — الصور بتتكدس على محور زمني بيشبه نبضة قلب
- كل "ذروة" في الـ wave = يوم كتير فيه صور
- بتـ tap على الذروة فالصور بتظهر بـ spring explosion animation
- بيحكي "قصة" حياتك البصرية

**Implementation:** Canvas + Compose custom layout + Spring animations

---

### 7. 🤫 `Silent Story` — ملاحظات مخفية في الصور

**Steganography خفيفة** — بتحط ملاحظة نصية مخفية في الـ EXIF metadata:

- بتبص على صورة وبتسحب لأسفل → بتظهر ملاحظتك السرية
- مفيد لـ: "التقطت الصورة دي في يوم كان صعب"
- الملاحظة مش ظاهرة في أي gallery تاني

---

### 8. 📊 `Memoria Stats` — إحصائيات ذاكرتك

Dashboard جميل بيقولك:

- أكتر مكان صوّرت فيه
- أكتر شخص ظهر في صورك
- ألوان السنة دي vs السنة اللي فاتت
- أكتر شهر بتصوّر فيه
- عدد الصور اللي فيها نصوص / أرقام / وجوه

---

### 9. 📄 `TextLens` — استخراج المعلومات من الصور

**OCR + Entity Extraction ذكي:**

- استخراج أرقام التليفون تلقائياً مع زر "اتصل" أو "حفظ"
- استخراج الإيميلات مع زر "إرسال"
- استخراج الروابط مع زر "فتح"
- استخراج النصوص كاملة مع إمكانية النسخ
- دعم العربي والإنجليزي معاً

---

### 10. 🔎 `SmartSearch` — البحث بالمعنى

- "دور على الصور اللي فيها أرقام تليفون" → بيجيب كل الصور دي
- "صور دكتور الأسنان" → بيدور على صور + labels طبية + أرقام
- "اللقاء اللي كان في المطعم الأحمر" → semantic search حقيقي
- بيجمع keyword search + entity filter + vector similarity

---

---

## 🗜️ ضغط الصور الذكي — بدون فقدان الجودة

### المبدأ

مش "ضغط عشوائي" — نظام ذكي بيحلل الصورة وبيقترح **أفضل format وجودة** بناءً على محتواها.

### Smart Compression Engine

```
صورة مختارة للضغط
         ↓
┌────────────────────────────────────────┐
│  [1] تحليل محتوى الصورة               │
│      - صورة طبيعية (ألوان كتير)       │
│      - screenshot (ألوان flat)         │
│      - رسمة / مخطط                    │
│      - RAW / HDR                       │
├────────────────────────────────────────┤
│  [2] تحديد Format الأمثل               │
│      الطبيعي → AVIF (API 34+) / WebP   │
│      Screenshot → WebP Lossless        │
│      رسمة شفافة → WebP Lossless        │
│      HDR → Ultra HDR JPEG / HEIC       │
├────────────────────────────────────────┤
│  [3] حساب Quality الأمثل (SSIM)        │
│      هدف: SSIM ≥ 0.95 (لا فرق مرئي)   │
│      Binary search من quality 60→95   │
├────────────────────────────────────────┤
│  [4] عرض المقارنة Before/After          │
│      + توفير المساحة المتوقع           │
└────────────────────────────────────────┘
```

### Compression Profiles — للمستخدم يختار

| Profile | الوصف | الحجم المتوقع | الاستخدام |
|---|---|---|---|
| **🏆 Maximum Quality** | SSIM ≥ 0.98، فرق مش محسوس | -20% إلى -35% | صور مهمة |
| **⚖️ Balanced (افتراضي)** | SSIM ≥ 0.95، لا فرق مرئي | -40% إلى -55% | معظم الصور |
| **💾 Storage Saver** | SSIM ≥ 0.90، فرق طفيف جداً | -60% إلى -70% | screenshots |
| **📤 Share Optimized** | مضبوط للمشاركة ≤ 1MB | حسب الصورة | WhatsApp/Social |

### Smart Suggestions Engine

```kotlin
data class CompressionSuggestion(
    val targetFormat: ImageFormat,
    val estimatedSavingPercent: Int,
    val estimatedSavingMb: Float,
    val ssimScore: Float,          // 0.0 → 1.0 (1.0 = identical)
    val profile: CompressionProfile,
    val reasoning: String          // "Screenshot مناسب لـ WebP Lossless"
)

class SmartCompressionAdvisor @Inject constructor() {

    fun suggest(photoId: Long, currentFormat: ImageFormat, sizeMb: Float): List<CompressionSuggestion> {
        val suggestions = mutableListOf<CompressionSuggestion>()

        // اقتراح AVIF لو الجهاز بيدعمه
        if (Build.VERSION.SDK_INT >= 34 && currentFormat != ImageFormat.AVIF) {
            suggestions.add(CompressionSuggestion(
                targetFormat = ImageFormat.AVIF,
                estimatedSavingPercent = 50,
                estimatedSavingMb = sizeMb * 0.5f,
                ssimScore = 0.97f,
                profile = CompressionProfile.BALANCED,
                reasoning = "AVIF أصغر 50% من JPEG بنفس الجودة"
            ))
        }

        // اقتراح WebP كـ fallback
        if (currentFormat == ImageFormat.JPEG || currentFormat == ImageFormat.PNG) {
            suggestions.add(CompressionSuggestion(
                targetFormat = ImageFormat.WEBP,
                estimatedSavingPercent = 30,
                estimatedSavingMb = sizeMb * 0.3f,
                ssimScore = 0.96f,
                profile = CompressionProfile.BALANCED,
                reasoning = "WebP أصغر 30% من JPEG بدون فرق مرئي"
            ))
        }

        return suggestions.sortedByDescending { it.estimatedSavingPercent }
    }
}
```

### Batch Compression — ضغط مجموعة

- تحديد صور متعددة → "ضغط المحدد"
- تقدير المساحة قبل البدء: "هتوفّر 1.2 GB"
- Progress bar تفصيلي لكل صورة
- إمكانية إيقاف في أي وقت
- الصورة الأصلية بتتنقل لـ Trash تلقائياً (مش بتتحذف)

### واجهة المقارنة Before/After

```
┌──────────────────────────────────────────┐
│         📸 ضغط الصورة                   │
│  ──────────────────────────────────────  │
│  │   الأصلي    │    بعد الضغط   │       │
│  │   4.2 MB    │    890 KB      │       │
│  │   JPEG      │    AVIF        │       │
│  │─────────────┼────────────────│       │
│  │   [صورة]   ↔  [صورة]        │       │
│  │  (اسحب للمقارنة)             │       │
│  ──────────────────────────────────────  │
│  💾 هتوفّر 3.3 MB (79% أصغر)           │
│  ⭐ جودة: 97% (لا فرق مرئي)            │
│                                          │
│  [✓ تطبيق - AVIF]  [⚙️ إعدادات]       │
└──────────────────────────────────────────┘
```

---

## 🎬 ضغط الفيديوهات

### المكتبات المستخدمة

**الخيار الأساسي: `LightCompressor`** (مبني على MediaCodec API — hardware accelerated)
- Apache 2.0 license، مش LGPL
- بيستخدم hardware encoder مباشرة (أسرع من FFmpeg)
- Kotlin Coroutines native
- بيرجع progress كـ Flow

**الخيار التاني: `Transcoder-Android`** (MediaCodec بدون FFmpeg)
- يدعم crop، concatenation، audio processing

### Video Compression Presets

| Preset | الدقة | Bitrate | الحجم المتوقع | الاستخدام |
|---|---|---|---|---|
| **4K Ultra** | 3840×2160 | 20 Mbps | الأصلي | Archive |
| **1080p High** | 1920×1080 | 8 Mbps | -40% | ذكريات مهمة |
| **1080p Balanced** | 1920×1080 | 5 Mbps | -55% | الافتراضي |
| **720p** | 1280×720 | 2.5 Mbps | -70% | مشاركة |
| **WhatsApp** | 854×480 | 1.2 Mbps | -85% | WhatsApp / Telegram |
| **Smart Auto** | يحلل ويقرر | ذكي | -50~70% | ✅ مُوصى به |

### Smart Auto Compression

```kotlin
class VideoCompressionAdvisor @Inject constructor() {

    fun suggestPreset(videoFile: File, metadata: VideoMetadata): VideoPreset {
        val durationMin = metadata.durationMs / 60_000.0
        val currentBitrateMbps = metadata.bitrateBps / 1_000_000.0
        val resolution = metadata.width * metadata.height

        return when {
            // فيديو قصير وبيتره عالي → ضغط قوي
            durationMin < 1 && currentBitrateMbps > 20 ->
                VideoPreset.WHATSAPP_OPTIMIZED

            // 4K غير ضروري للذكريات → 1080p
            resolution >= 3840 * 2160 ->
                VideoPreset.FULL_HD_BALANCED

            // بيتره عالي بدون سبب → balanced
            currentBitrateMbps > 15 ->
                VideoPreset.FULL_HD_BALANCED

            // كويس أصلاً
            else -> VideoPreset.NONE
        }
    }
}
```

### Video Compression UI

```kotlin
// Progress كـ StateFlow
val compressionState: StateFlow<VideoCompressionState>

sealed class VideoCompressionState {
    object Idle : VideoCompressionState()
    data class Analyzing(val videoPath: String) : VideoCompressionState()
    data class InProgress(
        val progressPercent: Int,
        val currentFile: String,
        val processedCount: Int,
        val totalCount: Int,
        val elapsedMs: Long,
        val estimatedRemainingMs: Long
    ) : VideoCompressionState()
    data class Done(
        val savedMb: Float,
        val savedPercent: Int,
        val processedCount: Int
    ) : VideoCompressionState()
    data class Error(val message: String) : VideoCompressionState()
}
```

### Batch Video Compression

```kotlin
suspend fun compressVideos(
    videoUris: List<Uri>,
    preset: VideoPreset,
    onProgress: (VideoCompressionState) -> Unit
) {
    videoUris.forEachIndexed { index, uri ->
        VideoCompressor.start(
            context = context,
            uris = listOf(uri),
            isStreamable = true,
            configureWith = Configuration(
                quality = preset.quality,        // LOW / MEDIUM / HIGH / VERY_HIGH
                videoNames = listOf("compressed_${index}.mp4"),
                isMinBitrateCheckEnabled = true   // مش بيضغط لو أصغر من الـ target
            ),
            appSpecificStorageConfiguration = StorageConfiguration(
                saveAt = getCompressionOutputDir()
            ),
            listener = object : CompressionListener {
                override fun onProgress(index: Int, percent: Float) {
                    onProgress(VideoCompressionState.InProgress(
                        progressPercent = percent.toInt(),
                        currentFile = uri.lastPathSegment ?: "",
                        processedCount = index,
                        totalCount = videoUris.size,
                        elapsedMs = 0,
                        estimatedRemainingMs = 0
                    ))
                }
                override fun onSuccess(index: Int, size: Long, path: String?) { /* ... */ }
                override fun onFailure(index: Int, failureMessage: String) { /* ... */ }
                override fun onCancelled(index: Int) { /* ... */ }
            }
        )
        // الأصلي ينتقل لـ Trash بعد نجاح الضغط
        trashManager.moveToTrash(uri)
    }
}
```

---

## 🗑️ سلة المهملات والمفضلة

### سلة المهملات (Trash)

**المبدأ:** الحذف مش نهائي — الصور بتبقى في الـ Trash لـ 30 يوم قبل الحذف التلقائي.

```kotlin
@Entity(tableName = "trash")
data class TrashItem(
    @PrimaryKey val photoId: Long,          // MediaStore ID
    val originalPath: String,               // المسار الأصلي
    val originalAlbum: String,              // الألبوم الأصلي
    val thumbnailPath: String,              // thumbnail محفوظ محلياً
    val deletedAt: Long,                    // وقت الحذف
    val expiresAt: Long = deletedAt + 30L * 24 * 60 * 60 * 1000,  // +30 يوم
    val sizeBytes: Long,
    val mimeType: String,
    val source: TrashSource                 // USER_DELETE / COMPRESSION / DUPLICATE_CLEANUP
)

enum class TrashSource {
    USER_DELETE,          // المستخدم حذف يدوياً
    COMPRESSION,          // الأصلي بعد الضغط
    DUPLICATE_CLEANUP,    // Photo DNA حذف النسخة الأقل جودة
    VAULT_EVICT           // خرج من الـ Vault
}
```

```kotlin
class TrashManager @Inject constructor(
    private val trashDao: TrashDao,
    private val mediaStore: MediaStoreRepository
) {
    // نقل لـ Trash
    suspend fun moveToTrash(photoId: Long) {
        val photo = mediaStore.getPhoto(photoId)
        trashDao.insert(TrashItem(
            photoId = photoId,
            originalPath = photo.path,
            originalAlbum = photo.album,
            thumbnailPath = saveThumbnail(photoId),
            deletedAt = System.currentTimeMillis(),
            sizeBytes = photo.sizeBytes,
            mimeType = photo.mimeType,
            source = TrashSource.USER_DELETE
        ))
        mediaStore.moveToSystemTrash(photoId)  // Android 11+ MediaStore.createTrashRequest
    }

    // استرجاع من Trash
    suspend fun restore(photoId: Long) {
        val item = trashDao.getById(photoId) ?: return
        mediaStore.restoreFromTrash(photoId, item.originalPath)
        trashDao.delete(photoId)
    }

    // حذف نهائي
    suspend fun deletePermanently(photoId: Long) {
        trashDao.delete(photoId)
        mediaStore.permanentlyDelete(photoId)
    }

    // تنظيف تلقائي كل يوم عبر WorkManager
    suspend fun cleanExpired() {
        val expired = trashDao.getExpired(System.currentTimeMillis())
        expired.forEach { deletePermanently(it.photoId) }
    }
}
```

### شاشة سلة المهملات — UX

```
🗑️ سلة المهملات
━━━━━━━━━━━━━━━━━━━━━━━━━━━
⚠️  الصور هتتحذف تلقائياً بعد 30 يوم
📦 المساحة المُستخدمة: 2.3 GB

🔄 ترتيب: [الأحدث] [الأقدم] [الأكبر]

┌─ صورة.jpg ──────────────────────────┐
│ 📅 هيُحذف خلال 12 يوم              │
│ 💾 4.2 MB · من: Camera              │
│ [↩️ استرجاع]  [🗑️ حذف نهائي]       │
└──────────────────────────────────────┘

[🗑️ تفريغ السلة كلها]  [↩️ استرجاع الكل]
```

### نظام المفضلة (Favorites)

```kotlin
// Room Entity
@Entity(tableName = "favorites")
data class FavoriteItem(
    @PrimaryKey val photoId: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val note: String? = null                // ملاحظة اختيارية على المفضلة
)

// DAO
@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteItem>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE photoId = :photoId)")
    fun isFavorite(photoId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToFavorites(item: FavoriteItem)

    @Query("DELETE FROM favorites WHERE photoId = :photoId")
    suspend fun removeFromFavorites(photoId: Long)
}
```

**الـ UX في شاشة التفاصيل:**

```
┌─────────────────────────────────────┐
│        [صورة]                       │
│                                     │
│  ❤️  [تاب] → يتحول لـ ♥️ ويهتز     │
│  haptic feedback خفيف               │
│  tooltip: "أُضيف للمفضلة"           │
└─────────────────────────────────────┘
```

---

## 🔢 الفلاتر والترتيب — للصور والمجلدات

### Sort Options الكاملة

```kotlin
enum class SortBy {
    DATE_TAKEN,          // تاريخ التقاط الصورة (من EXIF)
    DATE_MODIFIED,       // تاريخ آخر تعديل
    DATE_ADDED,          // تاريخ الإضافة لـ MediaStore
    NAME,                // الاسم أبجدياً
    SIZE,                // الحجم
    TYPE,                // نوع الملف / الامتداد
    RESOLUTION,          // الدقة (عدد البكسل)
    DURATION,            // للفيديوهات: المدة
    SIMILARITY           // بعد Photo DNA: مجموعات المتشابه
}

enum class SortOrder {
    ASCENDING,           // تصاعدي (قديم → جديد / صغير → كبير)
    DESCENDING           // تنازلي (جديد → قديم / كبير → صغير)
}

data class SortConfig(
    val sortBy: SortBy = SortBy.DATE_TAKEN,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
    val groupBy: GroupBy = GroupBy.NONE
)

enum class GroupBy {
    NONE,
    DAY,                 // تجميع حسب اليوم
    MONTH,               // تجميع حسب الشهر
    YEAR,                // تجميع حسب السنة
    ALBUM,               // تجميع حسب الألبوم
    TYPE,                // تجميع حسب النوع
    LOCATION,            // تجميع حسب المدينة (من GPS)
    FACE                 // تجميع حسب الوجه (ML Kit)
}
```

### Filter Options

```kotlin
data class FilterConfig(
    // فلتر النوع
    val showImages: Boolean = true,
    val showVideos: Boolean = true,
    val showGifs: Boolean = true,
    val formats: Set<ImageFormat> = emptySet(),    // فارغ = كل الصيغ

    // فلتر الحجم
    val minSizeMb: Float? = null,
    val maxSizeMb: Float? = null,

    // فلتر التاريخ
    val dateRange: DateRange? = null,

    // فلتر الدقة
    val minResolutionMp: Float? = null,            // megapixels

    // فلتر المحتوى الذكي
    val hasText: Boolean? = null,                  // من OCR index
    val hasPhoneNumber: Boolean? = null,
    val hasFaces: Boolean? = null,
    val isFavorite: Boolean? = null,
    val isInTrash: Boolean = false,

    // فلتر الألوان
    val dominantColor: Color? = null               // Pixel Palette
)
```

### واجهة الفلاتر — M3 Expressive Bottom Sheet

```
┌──────────────────────────────────────────┐
│  ⚙️ ترتيب وفلترة                        │
│  ────────────────────────────────────── │
│  📅 الترتيب حسب:                        │
│  ●  التاريخ  ○ الاسم  ○ الحجم  ○ النوع │
│  ────────────────────────────────────── │
│  🔼🔽 الاتجاه:                          │
│  [🔽 تنازلي (افتراضي)]  [🔼 تصاعدي]   │
│  ────────────────────────────────────── │
│  📁 تجميع حسب:                          │
│  [بدون] [يوم] [شهر] [سنة] [مكان]      │
│  ────────────────────────────────────── │
│  🎛️ فلاتر:                             │
│  الحجم:   [__] MB إلى [__] MB          │
│  النوع:   [✓صور] [✓فيديو] [✓GIF]      │
│  الصيغة:  [JPEG] [PNG] [WEBP] [HEIC]   │
│  محتوى:   [فيها نص] [فيها وجوه]        │
│  ────────────────────────────────────── │
│       [إعادة ضبط]  [✓ تطبيق]          │
└──────────────────────────────────────────┘
```

### ترتيب المجلدات

```kotlin
// المجلدات بتتأثر بنفس نظام الـ Sort
data class FolderSortConfig(
    val sortBy: FolderSortBy = FolderSortBy.DATE_LATEST_PHOTO,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
    val showEmptyFolders: Boolean = false,
    val pinFavorites: Boolean = true               // المفضلة في الأعلى دايماً
)

enum class FolderSortBy {
    DATE_LATEST_PHOTO,   // حسب أحدث صورة في الألبوم
    DATE_CREATED,        // حسب تاريخ إنشاء الألبوم
    NAME,                // اسم الألبوم أبجدياً
    PHOTO_COUNT,         // عدد الصور
    TOTAL_SIZE           // الحجم الكلي
}
```

### Quick Sort Bar — في الـ Gallery مباشرة

```
[📅 التاريخ ↓] [💾 الحجم] [🔤 الاسم] [📐 النوع]   ← اضغط = اختر، اضغط تاني = عكس الاتجاه
```

---

## 📝 نظام OCR — حل مشكلة العربي

### المشكلة

**ML Kit Text Recognition v2 لا يدعم العربية** — المودل بيتعامل مع character sets فقط (Latin, Chinese, Devanagari, Japanese, Korean).

### الحل: نظام هجين ذكي

```
صورة دخلت
     ↓
ML Kit Text Recognition v2
(Latin / أرقام / رموز) ← سريع جداً ~140ms
     ↓
نتيجة فيها Arabic characters?
     ↓ نعم               ↓ لا
Tesseract4Android      الانتهاء
+ ara.traineddata
~800ms
     ↓
دمج النتيجتين + Entity Extraction
(Phone / Email / URL / Arabic text)
```

### مقارنة خيارات OCR العربي

| الأداة | دقة العربي | حجم | سرعة | تعقيد التنفيذ |
|---|---|---|---|---|
| **Tesseract4Android + ara** | جيدة | ~30MB | ~800ms | سهل ✅ |
| **PaddleOCR ONNX** | ممتازة | ~15MB | ~200ms | معقد |
| **Gemini Nano (Android 15+)** | ممتازة | مبني في الجهاز | سريع | API بسيط ✅ |

**التوصية النهائية:**
- **Tesseract4Android** للـ fallback على كل الأجهزة
- **Gemini Nano** على الأجهزة اللي بتدعمه (Pixel 9+, Samsung S25+)
- **ML Kit** للـ Latin/أرقام دايماً (الأسرع)

### Entity Extractor — Regex Patterns

```kotlin
object EntityExtractor {
    // أرقام التليفون المصرية والدولية
    val PHONE = Regex("""(\+?20|0)?1[0125]\d{8}|\+?\d[\d\s\-()]{7,15}\d""")
    
    // إيميلات
    val EMAIL = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
    
    // URLs
    val URL = Regex("""https?://[^\s]+|www\.[^\s]+""")
    
    // أرقام عربية
    val ARABIC_NUMBERS = Regex("""[\u0660-\u0669]+""")
    
    fun extract(text: String): ExtractedEntities {
        return ExtractedEntities(
            phones = PHONE.findAll(text).map { it.value }.toList(),
            emails = EMAIL.findAll(text).map { it.value }.toList(),
            urls = URL.findAll(text).map { it.value }.toList()
        )
    }
}
```

---

## 🧠 نظام RAG المحلي — البحث الذكي

### Pipeline الـ Indexing (الفهرسة)

```
صورة جديدة/موجودة
         ↓
┌────────────────────────────────────────────┐
│  [1] ML Kit Image Labeler                  │
│      → "car", "beach", "food", "receipt"   │
│                                            │
│  [2] ML Kit Text (Latin/أرقام)             │
│      → "John 0123456789 john@email.com"    │
│                                            │
│  [3] Tesseract (Arabic fallback)           │
│      → "اتصل بنا: 01234567890"            │
│                                            │
│  [4] Regex Entity Extractor                │
│      → {phones: [...], emails: [...]}      │
│                                            │
│  [5] MediaPipe Text Embedder               │
│      → Float32Array[512]                   │
└────────────────────────────────────────────┘
         ↓
ObjectBox Vector DB + Room FTS5
```

### Pipeline البحث

```
المستخدم: "أرقام التليفون"
         ↓
┌────────────────────────────────────────────┐
│  [A] Room FTS5 Keyword Search              │
│      → صور فيها كلمة "تليفون" في labels   │
│                                            │
│  [B] Entity Type Filter                    │
│      → WHERE entity_type = 'PHONE_NUMBER'  │
│                                            │
│  [C] ObjectBox Semantic Vector Search      │
│      → Nearest Neighbor على الـ embeddings │
└────────────────────────────────────────────┘
         ↓
Reciprocal Rank Fusion (RRF)
→ دمج النتائج الثلاثة وترتيبها بالصلة
         ↓
نتائج مرتبة مع preview للنص المستخرج
```

### Vector Database

**ObjectBox 4.0** — أول on-device vector database لـ Android وKotlin:

- On-device بالكامل، مفيش cloud
- أسرع من SQLite في كل CRUD operations
- Native Kotlin API بدون SQL queries
- يستهلك 30MB RAM فقط

**Embedding Model:**
- **EmbeddingGemma 308M** (Google AI Edge) — على الأجهزة القوية
- **MediaPipe Text Embedder** — الـ fallback الأخف

---

## 🎛️ نظام Feature Flags — الميزات الاختيارية

### المبدأ

كل ميزة ذكية:
- **مفعّلة by default** (ما عدا Decoy Vault)
- قابلة للتعطيل من الإعدادات
- الميزات اللي تحتاج تحميل بتسأل المستخدم قبل التحميل

### تعريف الـ Features

```kotlin
enum class SmartFeature(
    val key: String,
    val defaultEnabled: Boolean = true,
    val requiresDownload: Boolean = false,
    val downloadSizeMb: Int = 0
) {
    OCR_LATIN(
        key = "ocr_latin",
        defaultEnabled = true,
        requiresDownload = false        // مدمج في ML Kit
    ),
    OCR_ARABIC(
        key = "ocr_arabic",
        defaultEnabled = true,
        requiresDownload = true,
        downloadSizeMb = 12             // ara.traineddata
    ),
    SMART_LABELS(
        key = "smart_labels",
        defaultEnabled = true,
        requiresDownload = false        // ML Kit Image Labeler
    ),
    RAG_SEARCH(
        key = "rag_search",
        defaultEnabled = true,
        requiresDownload = true,
        downloadSizeMb = 45             // EmbeddingGemma 308M Q4
    ),
    FACE_DETECTION(
        key = "face_detection",
        defaultEnabled = true,
        requiresDownload = false
    ),
    VIBE_ALBUMS(
        key = "vibe_albums",
        defaultEnabled = true,
        requiresDownload = false
    ),
    PIXEL_PALETTE(
        key = "pixel_palette",
        defaultEnabled = true,
        requiresDownload = false
    ),
    PHOTO_DNA(
        key = "photo_dna",
        defaultEnabled = true,
        requiresDownload = false
    ),
    MEMORY_MAP(
        key = "memory_map",
        defaultEnabled = true,
        requiresDownload = false
    ),
    TEMPORAL_WAVE(
        key = "temporal_wave",
        defaultEnabled = true,
        requiresDownload = false
    ),
    DECOY_VAULT(
        key = "decoy_vault",
        defaultEnabled = false          // opt-in فقط
    ),
    SILENT_STORY(
        key = "silent_story",
        defaultEnabled = true,
        requiresDownload = false
    ),
    SMART_COMPRESSION(
        key = "smart_compression",
        defaultEnabled = true,
        requiresDownload = false        // SSIM محلي بدون موديلات خارجية
    ),
    VIDEO_COMPRESSION(
        key = "video_compression",
        defaultEnabled = true,
        requiresDownload = false        // LightCompressor مدمج
    ),
    TRASH_BIN(
        key = "trash_bin",
        defaultEnabled = true,
        requiresDownload = false
    ),
    FAVORITES(
        key = "favorites",
        defaultEnabled = true,
        requiresDownload = false
    ),
    ADVANCED_SORT(
        key = "advanced_sort",
        defaultEnabled = true,
        requiresDownload = false
    ),
    MULTI_FORMAT_SUPPORT(
        key = "multi_format",
        defaultEnabled = true,
        requiresDownload = false        // avif-coder مدمج
    )
}
```

### FeatureFlagManager

```kotlin
@Singleton
class FeatureFlagManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    fun isEnabled(feature: SmartFeature): Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[booleanPreferencesKey(feature.key)]
                ?: feature.defaultEnabled
        }

    suspend fun setEnabled(feature: SmartFeature, enabled: Boolean) {
        dataStore.edit {
            it[booleanPreferencesKey(feature.key)] = enabled
        }
    }
}
```

### شاشة الإعدادات — تصميم UX

```
⚙️ الميزات الذكية
━━━━━━━━━━━━━━━━━━━━━━━━━━━
🤖 التعرف على النصوص (OCR)
  ┌──────────────────────────────────┐
  │ 🔤 نصوص إنجليزي / أرقام   [✓] │
  │ عع نصوص عربية              [✓] │
  │    📥 تحميل موديل (12MB)        │
  └──────────────────────────────────┘

🧠 البحث الذكي (RAG)              [✓]
   📥 يحتاج تحميل 45MB
   "تقدر تدور بجمل طبيعية"

🏷️ تصنيف المحتوى تلقائياً        [✓]
👤 التعرف على الوجوه              [✓]
🎨 البحث بالألوان (Pixel Palette)  [✓]
🧬 كشف الصور المتشابهة (Photo DNA) [✓]
📍 خريطة الذكريات                 [✓]
🌊 عرض الموجة الزمنية             [✓]
🤫 الملاحظات المخفية              [✓]

⚠️ متقدم
🔐 الخزينة المزدوجة (Decoy Vault)  [✗]
   "تفعيل خزينة بـ PIN مزيف"

━━━━━━━━━━━━━━━━━━━━━━━━━━━
[🔄 إعادة فهرسة كل الصور]
[🗑️ حذف كل بيانات الفهرسة]
```

---

## 🏗️ Architecture الكاملة

### Module Structure

```
📦 OmniMemoria/
│
├── 🎨 :ui
│   ├── gallery/
│   │   ├── GalleryScreen.kt          ← Grid الرئيسي
│   │   ├── TemporalWaveScreen.kt     ← عرض الموجة
│   │   └── GalleryViewModel.kt
│   │
│   ├── search/
│   │   ├── SmartSearchScreen.kt      ← البحث الذكي
│   │   ├── SearchResultsScreen.kt
│   │   └── SearchViewModel.kt
│   │
│   ├── detail/
│   │   ├── PhotoDetailScreen.kt      ← عارض الصورة
│   │   ├── TextLensOverlay.kt        ← overlay الـ OCR
│   │   └── SilentStorySheet.kt       ← الملاحظات المخفية
│   │
│   ├── albums/
│   │   ├── AlbumsScreen.kt
│   │   ├── VibeAlbumsScreen.kt       ← ألبومات المزاج
│   │   └── MemoryMapScreen.kt        ← خريطة الذكريات
│   │
│   ├── stats/
│   │   └── MemoriaStatsScreen.kt     ← الإحصائيات
│   │
│   ├── compression/
│   │   ├── ImageCompressionScreen.kt    ← Before/After Slider
│   │   ├── VideoCompressionScreen.kt    ← Presets + Progress
│   │   └── BatchCompressionScreen.kt    ← Batch + Savings Summary
│   │
│   ├── trash/
│   │   └── TrashScreen.kt               ← سلة المهملات + auto-expire
│   │
│   ├── favorites/
│   │   └── FavoritesScreen.kt
│   │
│   │   └── DecoyVaultManager.kt      ← الخزينة المزدوجة
│   │
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   └── FeatureToggleScreen.kt    ← الميزات الاختيارية
│   │
│   └── theme/
│       ├── Theme.kt                  ← M3 Expressive
│       ├── Color.kt                  ← Dynamic Color
│       ├── Shape.kt                  ← 35 shape system
│       └── Motion.kt                 ← Spring physics
│
├── 🧠 :domain
│   ├── indexing/
│   │   ├── IndexingUseCase.kt
│   │   ├── OcrUseCase.kt
│   │   └── EmbeddingUseCase.kt
│   │
│   ├── search/
│   │   ├── SmartSearchUseCase.kt     ← RRF هنا
│   │   ├── EntityExtractor.kt        ← Phone/Email/URL regex
│   │   └── SearchQuery.kt
│   │
│   ├── compression/
│   │   ├── SmartCompressionAdvisor.kt   ← SSIM-based suggestions
│   │   ├── ImageCompressor.kt           ← AVIF/WebP/HEIC encode
│   │   ├── VideoCompressionAdvisor.kt   ← preset selector
│   │   └── VideoCompressor.kt           ← LightCompressor wrapper
│   │
│   ├── trash/
│   │   ├── TrashManager.kt
│   │   └── TrashCleanupUseCase.kt       ← auto-expire 30 days
│   │
│   ├── favorites/
│   │   └── FavoritesUseCase.kt
│   │
│   ├── sorting/
│   │   ├── SortConfig.kt
│   │   ├── FilterConfig.kt
│   │   └── MediaSorter.kt               ← in-memory sort + Room ORDER BY
│   │

│   │   ├── PhotoDnaUseCase.kt        ← pHash
│   │   └── MemoryMapUseCase.kt
│   │
│   ├── ocr/
│   │   └── OcrOrchestrator.kt        ← يوزّع بين ML Kit / Tesseract
│   │
│   └── features/
│       └── FeatureFlagManager.kt
│
├── 💾 :data
│   ├── mediastore/
│   │   ├── MediaStoreRepository.kt
│   │   └── MediaStorePagingSource.kt ← Paging 3
│   │
│   ├── room/
│   │   ├── AppDatabase.kt
│   │   ├── PhotoIntelligence.kt      ← Entity + FTS5
│   │   ├── PhotoDao.kt
│   │   └── SearchDao.kt
│   │
│   ├── objectbox/
│   │   ├── VectorStore.kt            ← Embeddings storage
│   │   └── VectorSearchEngine.kt     ← ANN search
│   │
│   ├── ocr/
│   │   ├── MlKitOcrEngine.kt         ← Latin/أرقام ~140ms
│   │   ├── TesseractArabicOcr.kt     ← العربي ~800ms
│   │   └── OcrOrchestratorImpl.kt    ← يشغّل الاتنين ويدمج
│   │
│   └── ml/
│       ├── ImageLabeler.kt           ← ML Kit Image Labeling
│       ├── FaceDetector.kt           ← ML Kit Face Detection
│       ├── PaletteExtractor.kt       ← AndroidX Palette
│       ├── EmbeddingEngine.kt        ← MediaPipe / EmbeddingGemma
│       └── PHashCalculator.kt        ← Perceptual Hash
│
└── 🔧 :core
    ├── workers/
    │   ├── PhotoIndexWorker.kt       ← WorkManager worker
    │   └── ReindexWorker.kt
    ├── di/
    │   └── AppModule.kt              ← Hilt modules
    ├── flags/
    │   └── FeatureFlags.kt
    └── utils/
        ├── ImageUtils.kt
        └── CryptoUtils.kt            ← AES-256 للـ Vault
```

### Navigation Graph

```
NavHost
├── GalleryRoute (start)
│   ├── → PhotoDetailRoute
│   ├── → SmartSearchRoute
│   ├── → AlbumsRoute
│   │   ├── → VibeAlbumDetailRoute
│   │   └── → MemoryMapRoute
│   ├── → TemporalWaveRoute
│   ├── → MemoriaStatsRoute
│   ├── → VaultRoute (PIN protected)
│   └── → SettingsRoute
│       └── → FeatureToggleRoute
```

### State Management — MVI

```kotlin
// كل شاشة بتيجي معاها:
data class GalleryUiState(
    val photos: LazyPagingItems<Photo>,
    val isLoading: Boolean,
    val activeFeatures: Set<SmartFeature>,
    val selectedPhotos: Set<Long>
)

sealed class GalleryUiEvent {
    data class PhotoClick(val id: Long) : GalleryUiEvent()
    data class SearchQuery(val query: String) : GalleryUiEvent()
    object ToggleSelectionMode : GalleryUiEvent()
}

sealed class GalleryEffect {
    data class NavigateToDetail(val id: Long) : GalleryEffect()
    object ShowSearchSheet : GalleryEffect()
}
```

---

## 🛠️ Tech Stack

| الغرض | الأداة | السبب |
|---|---|---|
| **UI Framework** | Jetpack Compose + M3 Expressive | أحدث وأسرع |
| **Image Compression** | **avif-coder** + Bitmap.compress() | AVIF/HEIC encode + SSIM |
| **Video Compression** | **LightCompressor** (MediaCodec) | hardware-accelerated, no FFmpeg LGPL |
| **Format Support** | **avif-coder** + Coil 3 plugins | 15+ صيغة |
| **SSIM Quality** | Custom SSIM impl (pure Kotlin) | قياس الجودة بدون فرق مرئي |
| **Trash / Favorites** | **Room** + MediaStore.createTrashRequest | Android 11+ native trash |
| **Sorting / Filtering** | Room ORDER BY + Kotlin in-memory | سريع مع Paging 3 |
| **Image Loading** | **Coil 3** | أسرع من Glide في Compose |
| **OCR (Latin)** | **ML Kit Text v2** | مدمج، 140ms، مجاني |
| **OCR (Arabic)** | **Tesseract4Android + ara.traineddata** | الخيار الوحيد المجاني offline |
| **Image Labeling** | **ML Kit Image Labeling** | on-device، دقيق |
| **Face Detection** | **ML Kit Face Detection** | on-device، سريع |
| **Color Analysis** | **AndroidX Palette API** | مدمج في Jetpack |
| **Vector DB** | **ObjectBox 4.0** | أول وأسرع on-device vector DB لـ Android |
| **Embeddings** | **MediaPipe Text Embedder** + EmbeddingGemma 308M | حسب الجهاز |
| **Database** | **Room + FTS5** | keyword search سريع |
| **Background** | **WorkManager** | background indexing |
| **DI** | **Hilt** | standard |
| **Paging** | **Paging 3** | MediaStore الكبير |
| **Crypto** | **AES-256 + Android Keystore** | Decoy Vault |
| **Maps** | **Maps Compose** | Memory Map |
| **Preferences** | **DataStore** | Feature Flags |
| **Animations** | **Spring APIs في Compose** | M3 Expressive feel |

---

## 💾 Database Schema

### Room — Metadata + FTS5 + الجداول الجديدة

```kotlin
@Entity(tableName = "photo_intelligence")
data class PhotoIntelligence(
    @PrimaryKey
    val photoId: Long,

    // OCR Results
    val rawText: String?,
    val arabicText: String?,
    val latinText: String?,

    // Extracted Entities (JSON arrays)
    val phoneNumbers: String?,
    val emails: String?,
    val urls: String?,

    // ML Labels (JSON array)
    val labels: String?,

    // Color Analysis (JSON array of hex)
    val dominantColors: String?,

    // Face Detection
    val faceCount: Int = 0,

    // Perceptual Hash for Photo DNA
    val pHash: Long? = null,

    // GPS (from EXIF)
    val latitude: Double? = null,
    val longitude: Double? = null,

    // Compression history
    val compressionFormat: String? = null,    // الصيغة بعد الضغط
    val compressionSavedBytes: Long = 0,      // المساحة المُوفَّرة
    val compressedAt: Long? = null,

    // Indexing Metadata
    val indexedAt: Long = System.currentTimeMillis(),
    val ocrEngineVersion: Int = 1,
    val embeddingVersion: Int = 1,
    val isIndexed: Boolean = false
)

// FTS5 Virtual Table
@Fts4(contentEntity = PhotoIntelligence::class)
@Entity(tableName = "photo_fts")
data class PhotoIntelligenceFts(
    val rawText: String,
    val arabicText: String,
    val labels: String,
    val phoneNumbers: String,
    val emails: String
)

// Trash
@Entity(tableName = "trash")
data class TrashItem(
    @PrimaryKey val photoId: Long,
    val originalPath: String,
    val originalAlbum: String,
    val thumbnailPath: String,
    val deletedAt: Long,
    val expiresAt: Long = deletedAt + 30L * 24 * 60 * 60 * 1000,
    val sizeBytes: Long,
    val mimeType: String,
    val source: String            // TrashSource.name()
)

// Favorites
@Entity(tableName = "favorites")
data class FavoriteItem(
    @PrimaryKey val photoId: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val note: String? = null
)

// Sort/Filter Presets (يحفظ إعدادات المستخدم)
@Entity(tableName = "sort_presets")
data class SortPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,             // "صوري الكبيرة"
    val sortByJson: String,       // SortConfig serialized
    val filterJson: String,       // FilterConfig serialized
    val isDefault: Boolean = false
)
```

### ObjectBox — Vector Embeddings

```kotlin
@Entity
data class PhotoEmbedding(
    @Id var id: Long = 0,
    val photoId: Long,                          // FK to MediaStore

    @HnswIndex(dimensions = 512)
    val embedding: FloatArray,                  // 512-dim vector

    val embeddedText: String,                   // النص اللي اتـ embed منه
    val createdAt: Long = System.currentTimeMillis()
)
```

---

## ⚡ Background Indexing — WorkManager

### الـ Worker الرئيسي

```kotlin
class PhotoIndexWorker @AssistedInject constructor(
    context: Context,
    params: WorkerParameters,
    private val ocrOrchestrator: OcrOrchestrator,
    private val imageLabeler: ImageLabeler,
    private val paletteExtractor: PaletteExtractor,
    private val embeddingEngine: EmbeddingEngine,
    private val featureFlags: FeatureFlagManager,
    private val repository: IntelligenceRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val photoId = inputData.getLong("photo_id", -1L)
        if (photoId == -1L) return Result.failure()

        return try {
            val bitmap = loadBitmap(photoId)

            // OCR — بس لو مفعّل
            val ocrResult = buildOcrResult(bitmap, photoId)

            // Labels — بس لو مفعّل
            val labels = if (featureFlags.isEnabled(SMART_LABELS).first())
                imageLabeler.label(bitmap)
            else emptyList()

            // Colors — بس لو مفعّل
            val colors = if (featureFlags.isEnabled(PIXEL_PALETTE).first())
                paletteExtractor.extract(bitmap)
            else emptyList()

            // pHash — بس لو Photo DNA مفعّل
            val pHash = if (featureFlags.isEnabled(PHOTO_DNA).first())
                PHashCalculator.calculate(bitmap)
            else null

            // Embedding — بس لو RAG مفعّل
            val embeddingText = "${ocrResult.rawText} ${labels.joinToString(" ")}"
            val embedding = if (featureFlags.isEnabled(RAG_SEARCH).first())
                embeddingEngine.embed(embeddingText)
            else null

            // حفظ كل حاجة
            repository.saveIntelligence(
                photoId = photoId,
                ocrResult = ocrResult,
                labels = labels,
                colors = colors,
                pHash = pHash,
                embedding = embedding
            )

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }

    private suspend fun buildOcrResult(bitmap: Bitmap, photoId: Long): OcrResult {
        val latin = if (featureFlags.isEnabled(OCR_LATIN).first())
            ocrOrchestrator.recognizeLatin(bitmap)
        else ""

        val arabic = if (featureFlags.isEnabled(OCR_ARABIC).first())
            ocrOrchestrator.recognizeArabic(bitmap)
        else ""

        val combined = "$latin $arabic"
        val entities = EntityExtractor.extract(combined)

        return OcrResult(
            rawText = combined,
            latinText = latin,
            arabicText = arabic,
            entities = entities
        )
    }
}
```

### جدولة الـ Workers

```kotlin
// صورة جديدة → indexing فوري
fun scheduleImmediateIndex(photoId: Long) {
    val request = OneTimeWorkRequestBuilder<PhotoIndexWorker>()
        .setInputData(workDataOf("photo_id" to photoId))
        .setConstraints(Constraints(requiresBatteryNotLow = true))
        .build()
    WorkManager.getInstance(context).enqueue(request)
}

// فهرسة دورية للصور الجديدة كل 6 ساعات
fun schedulePeriodicIndex() {
    val request = PeriodicWorkRequestBuilder<BatchIndexWorker>(6, TimeUnit.HOURS)
        .setConstraints(
            Constraints(
                requiresDeviceIdle = true,          // بس لما التليفون مش بيتشغل
                requiresBatteryNotLow = true,
                requiresStorageNotLow = true
            )
        )
        .build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork("batch_index", KEEP, request)
}
```

---

## 📊 مقارنة المنافسين — الصورة الكاملة

| الميزة | Google Photos | PicSort | Simple Gallery | **OmniMemoria** |
|---|---|---|---|---|
| **OCR نصوص** | ✅ cloud فقط | ❌ | ❌ | ✅ **محلي 100%** |
| **OCR عربي** | ✅ cloud فقط | ❌ | ❌ | ✅ **Tesseract offline** |
| **استخراج أرقام** | ✅ cloud | ❌ | ❌ | ✅ **محلي** |
| **RAG / بحث دلالي** | ❌ | ❌ | ❌ | ✅ **ObjectBox** |
| **ضغط صور ذكي** | ❌ | ❌ | ❌ | ✅ **AVIF/WebP + SSIM** |
| **ضغط فيديو** | ❌ | ❌ | ❌ | ✅ **LightCompressor** |
| **سلة مهملات 30 يوم** | ✅ cloud | ❌ | ❌ | ✅ **محلي** |
| **مفضلة** | ✅ | ✅ | ✅ | ✅ |
| **ترتيب بالحجم/الاسم** | جزئياً | ❌ | ✅ | ✅ **+ 8 خيارات** |
| **فلترة بالصيغة** | ❌ | ❌ | ❌ | ✅ **15+ صيغة** |
| **AVIF / HEIC / RAW** | ✅ | ❌ | جزئياً | ✅ **كامل** |
| **Vibe Albums** | ❌ | ❌ | ❌ | ✅ |
| **Temporal Wave** | ❌ | ❌ | ❌ | ✅ |
| **Decoy Vault** | ❌ | ❌ | ❌ | ✅ |
| **Pixel Palette** | ❌ | ❌ | ❌ | ✅ |
| **Photo DNA (pHash)** | ✅ cloud | ❌ | ❌ | ✅ **محلي** |
| **Memory Map** | ✅ | ❌ | ❌ | ✅ |
| **Memoria Stats** | جزئياً | ❌ | ❌ | ✅ |
| **Silent Story** | ❌ | ❌ | ❌ | ✅ |
| **Feature Toggles** | ❌ | ❌ | ❌ | ✅ |
| **100% Offline** | ❌ | ✅ | ✅ | ✅ |
| **M3 Expressive** | جزئياً | ❌ | ❌ | ✅ **كامل** |
| **Android 15/16 APIs** | ✅ | ❌ | ❌ | ✅ |
| **مجاني بالكامل** | freemium | freemium | ✅ | ✅ |

---

## 🗓️ خطة التنفيذ — Phases

### Phase 1 — الأساس المعماري 🏗️ (أسبوعان)

**الهدف:** تطبيق شغّال بالأساسيات بشكل ممتاز + قاعدة بيانات جاهزة من اليوم الأول

- [ ] إعداد المشروع: Gradle, Hilt, Room, Navigation
- [ ] **Room DB Schema كامل** — جداول `photo_intelligence`, `trash`, `favorites`, `sort_presets` + FTS5
- [ ] **ObjectBox Vector DB** — تهيئة الـ schema وكائن `PhotoEmbedding` من البداية (الـ schema لازم تكون جاهزة حتى لو الـ indexing هييجي بعدين)
- [ ] **Vault Architecture Foundation** 🔺 *(بدون UI — الأساس فقط)*
  - `VaultPhoto` entity في Room + `isVaultItem: Boolean` flag على المستوى الـ DB
  - `VaultRepository` stub بـ AES-256 key generation عبر Android Keystore
  - الـ `MediaStoreRepository` يكون "vault-aware" — الـ queries بتستثني الـ vault items تلقائياً
  - Navigation Graph بمساريين منفصلين: `mainNavGraph` و `vaultNavGraph` (stub بشاشة فارغة الآن)
- [ ] MediaStoreRepository + Paging 3
- [ ] Compose Grid الرئيسي بـ M3 Expressive
- [ ] Dynamic Color Theme + Shape System
- [ ] Photo Detail Screen مع Coil 3
- [ ] Basic Navigation بـ Predictive Back
- [ ] Edge-to-edge support (Android 16)
- [ ] Permission handling صح (READ_MEDIA_IMAGES)
- [ ] **دعم الصيغ المتعددة:** avif-coder + Coil plugins (AVIF, HEIC, WebP, GIF, RAW preview)

> ⚠️ **سبب تقديم Vault Foundation للـ Phase 1:** الـ Vault بيتطلب تغيير جذري في الـ Data Layer — فصل الصور المشفرة عن الـ MediaStore العادي، ومسارين مستقلين في الـ Navigation. لو بنيت كل الـ Repositories في Phase 1-7 بدون vault-awareness، هتضطر في Phase 8 تعيد كتابة أجزاء كبيرة منهم. الـ UI المعقد للـ Vault بييجي في Phase 8، لكن الأساس المعماري لازم يكون هنا.
>
> ⚠️ **سبب تقديم ObjectBox للـ Phase 1:** الـ RAG في Phase 5 والـ Background Indexing في Phase 4 بيعتمدوا على الـ Vector schema. لو استنيت تعمل الـ setup بعدين، هتضطر تعمل Refactoring كبير في كل الـ workers والـ repositories.

**Deliverable:** معرض صور شغّال بشكل جميل + Database و Architecture جاهزين للتوسع الكامل

---

### Phase 2 — Feature Flags والإعدادات 🎛️ (أسبوع) 🔺 *(أولوية مُقدَّمة)*

**الهدف:** بناء لوحة التحكم قبل بناء الميزات الثقيلة

- [ ] **DataStore setup** + بناء `FeatureFlagManager` الكامل (كل الـ 18 feature)
- [ ] **Settings Screen** بتصميم M3 Expressive
- [ ] هيكل الـ **WorkManager** الأساسي — حتى لو الـ `PhotoIndexWorker` بيطبع Log فاضي في البداية، لازم يكون registered ومحضَّر للـ Phase 4
- [ ] Download-on-demand dialog للـ Tesseract + EmbeddingGemma (الـ UI والمنطق بس، التنفيذ الفعلي في Phase 4)
- [ ] Sort Presets: حفظ إعدادات الفلترة المفضلة في Room

> ⚠️ **سبب تقديم Feature Flags للـ Phase 2:** الميزات الثقيلة في Phase 4 (OCR, Embeddings) بتحتاج toggles من أول يوم عشان الـ Testing يكون ممكن. لو بنيت الميزات بدون نظام Feature Flags، الـ Debugging هيكون جحيم ومش هتقدر تطفي ميزة بسرعة لو عملت مشكلة.

**Deliverable:** المستخدم يتحكم في كل ميزة + WorkManager جاهز للتوسع

---

### Phase 3 — التنظيم الأساسي 🗂️ (أسبوع ونص)

**الهدف:** الميزات الأساسية لأي Gallery — بدون اعتماد على بيانات الذكاء

- [ ] **Sort System كامل:** 8 خيارات ترتيب + تصاعدي/تنازلي
- [ ] **Filter System الأساسي:** فلتر بالنوع، الحجم، التاريخ، الصيغة *(البيانات دي موجودة في MediaStore — مش محتاج Phase 4)*
- [ ] **GroupBy:** تجميع حسب اليوم/الشهر/السنة/المكان
- [ ] Quick Sort Bar في الـ Gallery
- [ ] Filter Bottom Sheet بـ M3 Expressive
- [ ] **Favorites System:** إضافة/حذف + شاشة المفضلة
- [ ] ❤️ Heart animation على Detail Screen
- [ ] **Trash System:** سلة مهملات بـ 30 يوم auto-expire
- [ ] TrashScreen مع Restore/Delete permanent
- [ ] WorkManager للـ auto-cleanup كل يوم (يستخدم الـ infrastructure اللي بنيناها في Phase 2)

> ⚠️ **الفلاتر الذكية مش هنا!** فلترة المحتوى (بالألوان، الوجوه، النصوص، وجود أرقام تليفون) بتعتمد على بيانات Phase 4 اللي لسه ما اتبنتش. هتلاقيها في Phase 5 جنب الـ RAG Search.

**Deliverable:** تنظيم كامل وسلة مهملات ومفضلة — شغّالين بدون AI

---

### Phase 4A — استخراج بيانات الصورة 🔬 (أسبوعان)

**الهدف:** OCR + Labels + Colors + pHash — كل حاجة بتتحفظ في Room

- [ ] **Gemini Nano / AICore Check** 🔺 *(الجديد — أهم خطوة في الـ Phase)*
  ```kotlin
  // قبل أي initialize لـ Tesseract، اعمل check للـ AICore
  val aiCoreAvailable = AICoreSdk.isAvailable(context) // Android 15+ Pixel/Samsung حديث
  val ocrEngine: OcrEngine = when {
      aiCoreAvailable -> GeminiNanoOcrEngine(context)   // أسرع + أدق + مجاني RAM
      else            -> TesseractOcrEngine(context)    // Fallback للأجهزة الأقدم
  }
  ```
  - **Gemini Nano** (على الأجهزة اللي بتدعمه): OCR عربي/إنجليزي + Entity Extraction بـ prompt واحد، مفيش download إضافي
  - **Tesseract4Android** (Fallback): Singleton في `Dispatchers.IO`، تحميل مرة واحدة
- [ ] **ML Kit Image Labeling** + Face Detection integration
- [ ] **ML Kit Text v2** (Latin/أرقام) — دايماً أسرع خيار للـ Latin
- [ ] Arabic/Latin OCR Orchestrator (Gemini Nano أو Tesseract حسب الجهاز)
- [ ] **Entity Extractor** — Phone/Email/URL regex patterns
- [ ] OCR overlay UI على شاشة التفاصيل + quick actions (اتصل/حفظ/إرسال)
- [ ] **AndroidX Palette API** — color fingerprinting لكل صورة
- [ ] **pHash Calculator** — Perceptual Hash للـ Photo DNA
- [ ] **`PhotoIndexWorker` — المرحلة الأولى** — بيحفظ OCR + Labels + Colors + pHash في Room فقط
- [ ] جدولة الـ Workers: `scheduleImmediateIndex` و`schedulePeriodicIndex`

> 💡 **ليه الانفصال؟** الـ OCR + Labels + Colors + pHash بياخدوا RAM محدود وبيطلعوا نتايج صغيرة في Room. لو دمجتهم مع الـ Embedding (اللي بتلود موديل 300MB+) في نفس الـ Phase، هتجيبلك إحباط وتعقيد تشخيص المشاكل.

**Deliverable:** التطبيق بيفهم محتوى الصور ويحفظه في Room — جاهز لبناء الـ Vectors

---

### Phase 4B — تجهيز الـ Vector DB 🗄️ (أسبوع)

**الهدف:** توليد الـ Embeddings وملء ObjectBox — خطوة تمهيدية مباشرة قبل الـ RAG

- [ ] **MediaPipe Text Embedder** integration — على الأجهزة العادية
- [ ] **EmbeddingGemma 308M** (Google AI Edge) — على الأجهزة القوية (+2GB RAM)
- [ ] **RAM-based model selection** (من الـ `availableRamMb` check الموجود في الـ README)
- [ ] تحديث `PhotoIndexWorker` ليولّد الـ embedding من النص المستخرج في 4A ويحفظه في ObjectBox
- [ ] **Batch insert** للـ Embeddings (مش صورة صورة — وفّر البطارية والـ I/O)
- [ ] Re-index trigger لو المستخدم بدّل الموديل من الإعدادات

> 💡 **ملاحظة:** الـ `PhotoIndexWorker` هيكمل على نتايج Phase 4A الجاهزة في Room — مش بيعيد الـ OCR من أول وجديد، بس بيقرأ الـ `rawText` المحفوظ ويعمله embed.

**Deliverable:** ObjectBox مليان vectors — الـ Semantic Search جاهز يشتغل

---

### Phase 5 — البحث الدلالي والفلاتر الذكية 🔎 (أسبوع ونص)

**الهدف:** تتويج مجهود Phase 4A + 4B — البحث الكامل بالمعنى والمحتوى

- [ ] Room FTS5 Virtual Table + Full-text search queries
- [ ] **Reciprocal Rank Fusion (RRF)** — دمج نتائج Room FTS5 + ObjectBox Vector Search
- [ ] Smart Search UI مع quick filters
- [ ] تفعيل البحث باللغة الطبيعية: "دور على أرقام التليفون" / "صور المطعم الأحمر"
- [ ] نتائج مع preview للنص المستخرج
- [ ] **الفلاتر الذكية** *(انتقلت من Phase 3 — البيانات دلوقتي جاهزة من Phase 4A)*
  - فلتر "فيها نص / فيها وجوه / فيها أرقام تليفون" عبر Room queries
  - فلتر بالألوان الـ dominant (Pixel Palette data)
  - تحديث Filter Bottom Sheet ليشمل الفلاتر الجديدة دي

**Deliverable:** "دور على أرقام التليفون" يشتغل offline بدقة + فلترة ذكية بالمحتوى

---

### Phase 6 — ضغط الميديا الذكي 🗜️ (أسبوعان)

**الهدف:** ضغط ذكي للصور والفيديوهات بدون فقدان جودة

- [ ] **SmartCompressionAdvisor:** SSIM-based quality analysis
- [ ] Image format detection + AVIF/WebP/HEIC encoding (avif-coder)
- [ ] Before/After Comparison Slider UI
- [ ] Compression Profiles: 4 مستويات
- [ ] Batch Image Compression مع progress
- [ ] **VideoCompressionAdvisor:** Smart preset selector
- [ ] LightCompressor integration (MediaCodec hardware-accelerated)
- [ ] 6 Video Presets (4K → WhatsApp)
- [ ] Video compression progress UI + Batch Video Compression
- [ ] أصل الصورة/الفيديو ينتقل لـ Trash بعد الضغط

**Deliverable:** ضغط صور وفيديوهات بذكاء

---

### Phase 7 — الميزات البصرية الذكية 🎨 (أسبوعان)

**الهدف:** الـ UI/UX الدلع اللي بيعتمد على البيانات اللي جمعناها في Phase 4A

- [ ] **Pixel Palette** — Color Wheel UI + البحث بالألوان (البيانات جاهزة من Phase 4A)
- [ ] **Photo DNA** — Duplicates screen (pHash جاهز من Phase 4A)
- [ ] **Vibe Albums** — Rule engine فوق بيانات ML Kit (Labels جاهزة من Phase 4A)
- [ ] **Temporal Wave** — Canvas custom layout + Spring animations
- [ ] **Memoria Stats** — Dashboard إحصائيات كامل (OCR + Labels + Colors)
- [ ] **Memory Map** — Maps Compose + DBSCAN GPS clustering على الـ EXIF coordinates
  - ⚠️ بتحتاج Google Maps API Key — اتحركت هنا عمداً بعد ما الـ GPS data متاح من Phase 4A

> 💡 كل الميزات دي تتبنى بسرعة هنا لأن الـ data pipeline جاهز كلياً.

**Deliverable:** تطبيق بـ 6 ميزات بصرية فريدة مدعومة ببيانات حقيقية

---

### Phase 8 — إكمال الـ Vault UI والأمان 🔐 (أسبوعان)

**الهدف:** تحويل Vault Foundation من Phase 1 لتجربة كاملة — الأعقد تقنياً، محتاج تركيز منفرد

- [ ] **Decoy Vault UI الكامل** — بناء فوق الـ Architecture الموضوعة في Phase 1
  - شاشة إعداد الـ PIN الرئيسي والـ PIN المزيف
  - `vaultNavGraph` كامل مع الـ vault gallery screen
  - استيراد/تصدير الصور للـ Vault مع AES-256 encryption/decryption
  - التأكد إن صور الـ Vault مش بتظهر في MediaStore أو thumbnail cache
  - ⚠️ ابدأ بـ test على صور تجريبية عشان تتجنب Data Loss في حالة خطأ في الـ decryption
- [ ] **Silent Story** — EXIF metadata read/write
- [ ] Ultra HDR viewer (Android 16)

> 💡 **ليه Phase 8 وليس أبكر؟** الـ Vault UI لوحده معقد. بس الأمان الحقيقي كان من Phase 1 (الـ Architecture). هنا بس بنكمل الـ UX.

**Deliverable:** خزينة مشفرة كاملة + ملاحظات مخفية في الصور

---

### Phase 9 — Polish & Performance ✨ (أسبوع ونص)

**الهدف:** شكل احترافي وأداء ممتاز

- [ ] Spring animations على كل transition
- [ ] Shape morphing بين الشاشات
- [ ] Haptic feedback على كل action مهم
- [ ] Coil 3 caching optimization
- [ ] Paging 3 prefetch tuning
- [ ] Memory profiling و leak fixes
- [ ] Dark/Light theme refinement
- [ ] Accessibility (TalkBack support)
- [ ] Compression stats في Memoria Dashboard
- [ ] Sort presets UI polish

**Deliverable:** تطبيق جاهز للـ production

---

## ⚠️ تحذيرات تقنية مهمة (من تجارب الـ Tech Stack)

### 0. Gemini Nano / AICore — اتحقق قبل ما تلود Tesseract

على الأجهزة الحديثة (Pixel 9+، Samsung S25+، Android 15+)، الـ AICore بيوفر OCR + Entity Extraction بدون أي download إضافي وبدون استهلاك RAM إضافي. خليه أول check في الـ `OcrOrchestrator`:

```kotlin
class OcrOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tesseractEngine: TesseractEngine,   // lazy — مش بيتحمل غير لما يُستدعى
) {
    private val useAICore: Boolean by lazy {
        // AICore متاح على Android 15+ أجهزة بتدعمه
        Build.VERSION.SDK_INT >= 35 && AICoreSdk.isAvailable(context)
    }

    suspend fun recognizeArabic(bitmap: Bitmap): String {
        return if (useAICore) {
            // Gemini Nano: أسرع + أدق + مجاني RAM — مش محتاج download
            GeminiNanoOcr.recognize(context, bitmap, language = "ara")
        } else {
            // Fallback: Tesseract Singleton
            tesseractEngine.recognize(bitmap)
        }
    }
}
```

> 💡 الـ `TesseractEngine` كـ `lazy` — لو الجهاز بيدعم AICore، الـ Tesseract مش هيتحمل خالص. وفّر ~30MB RAM.

---



```kotlin
// ❌ غلط — بيعمل load جديد لكل صورة (~800ms × عدد الصور)
fun recognizeArabic(bitmap: Bitmap): String {
    val tessAPI = TessBaseAPI()
    tessAPI.init(dataPath, "ara")  // <-- COLD START هنا
    return tessAPI.getUTF8Text()
}

// ✅ صح — Singleton يعيش طول الـ Foreground
@Singleton
class TesseractEngine @Inject constructor(@ApplicationContext ctx: Context) {
    private val api: TessBaseAPI by lazy {
        TessBaseAPI().also { it.init(ctx.filesDir.path, "ara") }
    }

    // شغّل دايماً في Dispatchers.IO
    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        api.setImage(bitmap)
        api.utF8Text ?: ""
    }
}
```

### 2. Paging 3 مع Room + MediaStore — الـ Source of Truth

لو دمجت بيانات Room (مفضلة؟ مفهرس؟) مع بيانات MediaStore جوه نفس الـ PagingSource، ممكن يحصل Inconsistency لو الصورة اتمسحت من بره التطبيق.

```kotlin
// ✅ الحل — Room كـ Cache كامل أو Flow.combine للـ IDs
val photosWithMeta: Flow<PagingData<PhotoWithMeta>> = combine(
    mediaStorePager.flow,           // Source of Truth
    favoritesDao.getFavoriteIds()   // Room overlay
) { pagingData, favoriteIds ->
    pagingData.map { photo ->
        photo.copy(isFavorite = photo.id in favoriteIds)
    }
}
```

### 3. ObjectBox — Batch Insert للـ Embeddings

```kotlin
// ❌ غلط — بيعمل I/O transaction لكل صورة
photos.forEach { photo ->
    val embedding = embeddingEngine.embed(photo.text)
    embeddingBox.put(PhotoEmbedding(photoId = photo.id, embedding = embedding))
}

// ✅ صح — Batch insert واحد للكل
val embeddings = photos.map { photo ->
    PhotoEmbedding(
        photoId = photo.id,
        embedding = embeddingEngine.embed(photo.text),
        embeddedText = photo.text
    )
}
embeddingBox.put(embeddings)  // transaction واحدة بدل N transactions
```

---



### الأداء على الأجهزة المتوسطة

```kotlin
// تحقق من RAM قبل تشغيل الـ heavy features
val activityManager = context.getSystemService(ActivityManager::class.java)
val memInfo = ActivityManager.MemoryInfo()
activityManager.getMemoryInfo(memInfo)

val availableRamMb = memInfo.availMem / (1024 * 1024)

// اختار الـ embedding model المناسب
val embeddingModel = when {
    availableRamMb > 2000 -> EmbeddingModel.GEMMA_308M
    availableRamMb > 1000 -> EmbeddingModel.MEDIAPIPE_UNIVERSAL
    else -> EmbeddingModel.NONE  // disable RAG على الأجهزة الضعيفة
}
```

### Privacy بالكامل

- **مفيش أي API call** لأي server خارجي
- كل الـ AI يشتغل on-device
- Vault مشفّر بـ Android Keystore (المفتاح مش ممكن يتسرق)
- صور الـ Vault مش بتظهر في MediaStore

### Minimum Requirements

```
minSdk: 29 (Android 10)       ← 94%+ of active devices
targetSdk: 36 (Android 16)
compileSdk: 36

// Features إضافية على:
Android 13+: READ_MEDIA_IMAGES permission
Android 15+: Embedded Photo Picker
Android 16+: Ultra HDR in HEIC, Edge-to-edge إجباري
```

---

## 🚀 الخطوة الأولى — Setup

```bash
# إنشاء المشروع
# Android Studio → New Project → Empty Activity
# Minimum SDK: 29
# Language: Kotlin

# Gradle dependencies (app/build.gradle.kts)
dependencies {
    // Compose + M3
    implementation("androidx.compose.material3:material3:1.4.0-alpha13")

    // Coil 3 + format plugins
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-gif:3.1.0")
    implementation("io.coil-kt.coil3:coil-svg:3.1.0")

    // AVIF / HEIC support (15+ صيغة)
    implementation("io.github.awxkee:avif-coder:2.2.0")
    implementation("io.github.awxkee:avif-coder-coil:2.2.0")

    // ML Kit
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:face-detection:16.1.7")

    // Tesseract (Arabic OCR)
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.8.0")

    // ObjectBox (Vector DB for RAG)
    implementation("io.objectbox:objectbox-kotlin:4.0.3")

    // Room + FTS5
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // Video Compression (MediaCodec hardware-accelerated)
    implementation("com.abedelazizshe:light-compressor:1.4.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Paging 3
    implementation("androidx.paging:paging-compose:3.3.6")

    // DataStore (Feature Flags + Sort preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.4")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.54")
    ksp("com.google.dagger:hilt-compiler:2.54")

    // Maps Compose
    implementation("com.google.maps.android:maps-compose:6.4.4")

    // Palette API
    implementation("androidx.palette:palette-ktx:1.0.0")

    // AndroidX ExifInterface (Silent Story / GPS)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
```

---

*آخر تحديث: مايو 2026 — OmniMemoria Planning Document v2.2 (Vault Architecture في Phase 1 + تقسيم Phase 4 + Gemini Nano AICore + فصل الفلاتر الذكية)*

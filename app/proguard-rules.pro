# Tesseract resolves JNI entry points and callbacks by their Java names.
-keep class com.googlecode.tesseract.android.** { *; }
# Existing WorkManager database entries must survive obfuscation on updates.
-keepnames class com.omnimemoria.data.worker.** extends androidx.work.ListenableWorker

# Tesseract resolves JNI entry points and callbacks by their Java names.
-keep class com.googlecode.tesseract.android.** { *; }
# Existing WorkManager database entries must survive obfuscation on updates.
-keepnames class com.omnimemoria.data.worker.** extends androidx.work.ListenableWorker
# DataStore 1.1.5 shades protobuf. MessageSchema reads generated field names
# (preferences_, value_, valueCase_, strings_) reflectively. Renaming these
# crashes both reading existing preferences and serializing new preferences.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

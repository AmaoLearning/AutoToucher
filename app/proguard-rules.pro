# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified in the
# Android SDK tools/proguard/proguard-android-optimize.txt file.

# Room: keep entity/DAO class names
-keep class com.example.autotoucher.data.db.** { *; }

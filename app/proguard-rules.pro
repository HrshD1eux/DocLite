# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Line numbers for crash debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Annotations & Reflection metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Apache POI & XMLBeans
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.xmlbeans.**
-keep class schemasMicrosoftComOffice** { *; }
-keep class schemasMicrosoftComVml** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-dontwarn org.openxmlformats.schemas.**
-keep class org.etsi.uri.** { *; }
-dontwarn org.etsi.uri.**
-dontwarn org.apache.commons.**
-dontwarn com.github.virtuald.**
-keep class org.w3c.dom.** { *; }
-dontwarn org.w3c.dom.**
-dontwarn java.awt.**
-dontwarn javax.xml.stream.**
-dontwarn javax.xml.namespace.**
-dontwarn javax.xml.datatype.**

# PDFBox Android
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# OpenCSV
-keep class com.opencsv.** { *; }
-dontwarn com.opencsv.**

# Room Database & SQLite
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# DataStore & Preferences
-dontwarn androidx.datastore.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

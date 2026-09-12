# Line numbers for crash debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Annotations & Reflection metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Apache POI & XMLBeans
-keep class org.apache.poi.** { *; }
-keep interface org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-keep class org.apache.xmlbeans.** { *; }
-keep interface org.apache.xmlbeans.** { *; }
-dontwarn org.apache.xmlbeans.**
-keep class schemasMicrosoftComOffice** { *; }
-keep class schemasMicrosoftComVml** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep interface org.openxmlformats.schemas.** { *; }
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
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**

# PDFBox Android
-keep class com.tom_roush.pdfbox.** { *; }
-keep interface com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# OpenCSV
-keep class com.opencsv.** { *; }
-dontwarn com.opencsv.**

# Room Database, DAOs & Entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.HrshD1eux.DocLite.database.** { *; }
-keep class com.HrshD1eux.DocLite.database.entity.** { *; }
-keep class com.HrshD1eux.DocLite.database.dao.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# DataStore & Preferences Protobuf
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn androidx.datastore.**

# Models & Navigation Data Classes
-keep class com.HrshD1eux.DocLite.models.** { *; }
-keep class com.HrshD1eux.DocLite.bankstatement.model.** { *; }

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

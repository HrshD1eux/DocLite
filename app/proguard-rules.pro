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
-keep class schemaorg_apache_xmlbeans.** { *; }
-keep interface schemaorg_apache_xmlbeans.** { *; }
-keepclassmembers class schemaorg_apache_xmlbeans.** { *; }
-dontwarn schemaorg_apache_xmlbeans.**
-keep class schemasMicrosoftComOffice** { *; }
-keep class schemasMicrosoftComVml** { *; }
-keep class org.openxmlformats.** { *; }
-keep interface org.openxmlformats.** { *; }
-dontwarn org.openxmlformats.**
-keep class org.etsi.uri.** { *; }
-dontwarn org.etsi.uri.**
# Apache Commons & POI Companion Libraries
-keep class org.apache.commons.compress.** { *; }
-keep interface org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.collections4.** { *; }
-dontwarn org.apache.commons.collections4.**
-keep class org.apache.commons.codec.** { *; }
-dontwarn org.apache.commons.codec.**
-keep class org.apache.commons.math3.** { *; }
-dontwarn org.apache.commons.math3.**
-keep class org.apache.commons.lang3.** { *; }
-dontwarn org.apache.commons.lang3.**
-keep class org.apache.commons.text.** { *; }
-dontwarn org.apache.commons.text.**
-keep class com.github.virtuald.** { *; }
-dontwarn com.github.virtuald.**
-keep class com.zaxxer.sparsebitset.** { *; }
-dontwarn com.zaxxer.sparsebitset.**
-dontwarn org.apache.commons.**
-keep class org.w3c.dom.** { *; }
-dontwarn org.w3c.dom.**
-keep class com.fasterxml.aalto.** { *; }
-keep interface com.fasterxml.aalto.** { *; }
-dontwarn com.fasterxml.aalto.**
-keep class javax.xml.stream.** { *; }
-keep interface javax.xml.stream.** { *; }
-dontwarn javax.xml.stream.**
-dontwarn javax.xml.namespace.**
-dontwarn javax.xml.datatype.**
-dontwarn java.awt.**
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

package com.HrshD1eux.DocLite.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.HrshD1eux.DocLite.database.dao.DocumentMetadataDao
import com.HrshD1eux.DocLite.database.dao.FavoriteFileDao
import com.HrshD1eux.DocLite.database.dao.PasswordProtectionDao
import com.HrshD1eux.DocLite.database.dao.PdfAnnotationDao
import com.HrshD1eux.DocLite.database.dao.RecentFileDao
import com.HrshD1eux.DocLite.database.dao.TrashedFileDao
import com.HrshD1eux.DocLite.database.entity.DocumentMetadataEntity
import com.HrshD1eux.DocLite.database.entity.FavoriteFileEntity
import com.HrshD1eux.DocLite.database.entity.PasswordProtectionEntity
import com.HrshD1eux.DocLite.database.entity.PdfAnnotationEntity
import com.HrshD1eux.DocLite.database.entity.RecentFileEntity
import com.HrshD1eux.DocLite.database.entity.TrashedFileEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecentFileEntity::class,
        FavoriteFileEntity::class,
        PdfAnnotationEntity::class,
        DocumentMetadataEntity::class,
        PasswordProtectionEntity::class,
        TrashedFileEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao
    abstract fun favoriteFileDao(): FavoriteFileDao
    abstract fun pdfAnnotationDao(): PdfAnnotationDao
    abstract fun documentMetadataDao(): DocumentMetadataDao
    abstract fun passwordProtectionDao(): PasswordProtectionDao
    abstract fun trashedFileDao(): TrashedFileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pdf_annotations_fileUri` ON `pdf_annotations` (`fileUri`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `trashed_files` (
                        `uriString` TEXT NOT NULL,
                        `originalPath` TEXT NOT NULL,
                        `trashedPath` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `formatName` TEXT NOT NULL,
                        `trashedTimestamp` INTEGER NOT NULL,
                        `expiryTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`uriString`)
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "doclite_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

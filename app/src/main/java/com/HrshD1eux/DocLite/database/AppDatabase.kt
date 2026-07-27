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
import com.HrshD1eux.DocLite.database.entity.DocumentMetadataEntity
import com.HrshD1eux.DocLite.database.entity.FavoriteFileEntity
import com.HrshD1eux.DocLite.database.entity.PasswordProtectionEntity
import com.HrshD1eux.DocLite.database.entity.PdfAnnotationEntity
import com.HrshD1eux.DocLite.database.entity.RecentFileEntity

@Database(
    entities = [
        RecentFileEntity::class,
        FavoriteFileEntity::class,
        PdfAnnotationEntity::class,
        DocumentMetadataEntity::class,
        PasswordProtectionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao
    abstract fun favoriteFileDao(): FavoriteFileDao
    abstract fun pdfAnnotationDao(): PdfAnnotationDao
    abstract fun documentMetadataDao(): DocumentMetadataDao
    abstract fun passwordProtectionDao(): PasswordProtectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "doclite_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}


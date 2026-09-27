package app.masary.core.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        StudentProfileStateEntity::class,
        StudentSubjectStateEntity::class,
        CachedDocumentEntity::class,
        PendingOperationEntity::class,
        QuestionSessionEntity::class,
        QuestionAnswerEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MasaryLocalDatabase : RoomDatabase() {
    abstract fun studentStateDao(): StudentStateDao
    abstract fun cachedDocumentDao(): CachedDocumentDao
    abstract fun pendingOperationDao(): PendingOperationDao
    abstract fun questionSessionDao(): QuestionSessionDao

    companion object {
        @Volatile
        private var instance: MasaryLocalDatabase? = null

        fun get(context: Context): MasaryLocalDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MasaryLocalDatabase::class.java,
                    "masary_student_local.db",
                ).build().also { instance = it }
            }
    }
}

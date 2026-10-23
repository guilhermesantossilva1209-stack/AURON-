package br.com.auron.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        RoutineItemEntity::class,
        RoutineCompletionEntity::class,
        ChatMessageEntity::class,
        MetadataEntity::class,
        MemoryEntity::class,
        DailyReadingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AuronDatabase : RoomDatabase() {
    abstract fun dao(): AuronDao

    companion object {
        private val instances = mutableMapOf<String, AuronDatabase>()

        /** Additive upgrade of the exact 0.1 schema: no records or indexes are dropped. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE routine_items ADD COLUMN action TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS memories (id TEXT NOT NULL, ciphertext BLOB NOT NULL, iv BLOB NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memories_updatedAt ON memories (updatedAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS daily_readings (day TEXT NOT NULL, verseIdsJson TEXT NOT NULL, PRIMARY KEY(day))")
            }
        }

        fun databaseName(profileId: String = "local"): String = ProfileStorage.databaseName(profileId)

        fun open(context: Context, profileId: String = "local"): AuronDatabase = synchronized(instances) {
            val name = databaseName(profileId)
            instances.getOrPut(name) {
                Room.databaseBuilder(context.applicationContext, AuronDatabase::class.java, name)
                    .addMigrations(MIGRATION_1_2)
                    .build()
            }
        }
    }
}

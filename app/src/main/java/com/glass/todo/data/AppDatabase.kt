package com.glass.todo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TodoEntity::class, EventEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao
    abstract fun eventDao(): EventDao

    companion object {
        private const val DB_NAME = "glass_todo.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    // 同步 DAO 查询由调用方显式放到后台线程；这里不开启
                    // allowMainThreadQueries，避免以后误在主线程访问数据库。
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        /** 只增加可空时间列，不重建表，完整保留用户已有待办、事件和组件偏好。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN dueMinuteOfDay INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN targetMinuteOfDay INTEGER")
            }
        }
    }
}

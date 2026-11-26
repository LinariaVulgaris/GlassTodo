package com.glass.todo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TodoEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }

        /** 旧版本只增加可空时间列，不重建表，完整保留用户已有待办、事件和组件偏好。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN dueMinuteOfDay INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN targetMinuteOfDay INTEGER")
            }
        }

        /**
         * 把旧的日期排序固化成 sortOrder，之后列表完全按用户长按拖动后的顺序显示。
         * 只更新已有排序列，不重建表，也不删除或重写事项内容。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    WITH ranked AS (
                        SELECT id,
                               ROW_NUMBER() OVER (
                                   PARTITION BY done
                                   ORDER BY CASE WHEN dueEpochDay IS NULL THEN 1 ELSE 0 END,
                                            dueEpochDay,
                                            CASE WHEN dueMinuteOfDay IS NULL THEN -1 ELSE dueMinuteOfDay END,
                                            sortOrder,
                                            createdAt DESC,
                                            id DESC
                               ) - 1 AS position
                        FROM todos
                    )
                    UPDATE todos
                    SET sortOrder = (SELECT position FROM ranked WHERE ranked.id = todos.id)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    WITH ranked AS (
                        SELECT id,
                               ROW_NUMBER() OVER (
                                   ORDER BY targetEpochDay,
                                             CASE WHEN targetMinuteOfDay IS NULL THEN -1 ELSE targetMinuteOfDay END,
                                             sortOrder,
                                             id
                               ) - 1 AS position
                        FROM events
                    )
                    UPDATE events
                    SET sortOrder = (SELECT position FROM ranked WHERE ranked.id = events.id)
                    """.trimIndent()
                )
            }
        }

        /** 倒数日功能下线，按用户要求删除旧事件数据；待办表与排序保持不变。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS events")
            }
        }
    }
}

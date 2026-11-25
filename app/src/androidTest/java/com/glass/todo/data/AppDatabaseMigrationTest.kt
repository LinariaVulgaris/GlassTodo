package com.glass.todo.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2PreservesItemsAndAddsDisabledTimeColumns() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO todos (id, title, note, dueEpochDay, done, sortOrder, createdAt)
                VALUES (1, '保留待办', '备注', 20000, 0, 0, 100)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO events
                    (id, title, targetEpochDay, repeat, countUpWhenPast, archived, sortOrder, createdAt)
                VALUES (2, '保留事件', 20001, 'none', 1, 0, 0, 101)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            AppDatabase.MIGRATION_1_2
        )
        db.query("SELECT title, dueEpochDay, dueMinuteOfDay FROM todos WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("保留待办", cursor.getString(0))
            assertEquals(20000L, cursor.getLong(1))
            assertTrue(cursor.isNull(2))
        }
        db.query("SELECT title, targetEpochDay, targetMinuteOfDay FROM events WHERE id = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("保留事件", cursor.getString(0))
            assertEquals(20001L, cursor.getLong(1))
            assertTrue(cursor.isNull(2))
        }

        db.apply {
            execSQL("UPDATE todos SET dueMinuteOfDay = 1439 WHERE id = 1")
            execSQL("UPDATE events SET targetMinuteOfDay = 540 WHERE id = 2")
        }
        db.query("SELECT dueMinuteOfDay FROM todos WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1439, cursor.getInt(0))
        }
        db.query("SELECT targetMinuteOfDay FROM events WHERE id = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(540, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}

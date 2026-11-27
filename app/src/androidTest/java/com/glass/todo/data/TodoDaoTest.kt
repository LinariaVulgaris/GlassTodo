package com.glass.todo.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: TodoDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.todoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertIfAbsentDoesNotReplaceExistingTodo() = runBlocking {
        val original = TodoEntity(id = 7, title = "原待办", note = "原备注")
        dao.insert(original)

        dao.insertIfAbsent(original.copy(title = "不应覆盖", note = "不应覆盖"))

        assertEquals(original, dao.getByIdSync(original.id))
    }

    @Test
    fun concurrentInsertsReceiveUniqueSequentialSortOrders() = runBlocking {
        val ids = coroutineScope {
            (1..20).map { index ->
                async(Dispatchers.Default) {
                    dao.insertWithNextSortOrder(TodoEntity(title = "待办 $index"))
                }
            }.awaitAll()
        }

        val orders = ids.map { id -> requireNotNull(dao.getByIdSync(id)).sortOrder }.sorted()
        assertEquals((0 until ids.size).toList(), orders)
    }
}

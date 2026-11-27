package com.glass.todo.data

import android.content.Context
import androidx.room.withTransaction
import com.glass.todo.util.DayMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 极简依赖容器。
 *
 * 这个项目只有一个模块、依赖关系也很浅，用手动单例比引入 Hilt 更划算：
 * 少一个注解处理器，构建更快，组件那条链路上也不用处理注入时机问题。
 */
class Repository private constructor(context: Context) {

    private val db = AppDatabase.get(context)
    val prefs = Prefs(context)

    private val todoManualSort = MutableStateFlow(prefs.todoManualSortEnabled)
    val todoManualSortEnabled = todoManualSort.asStateFlow()

    /** 首次推断与用户主动切换共用一把锁，避免较晚完成的推断覆盖用户选择。 */
    private val sortModeLock = Any()

    /** 新建与拖拽排序串行化，确保追加项不会拿到被重排覆盖的顺序号。 */
    private val todoWriteMutex = Mutex()

    val todos: TodoDao get() = db.todoDao()

    suspend fun ensureTodoSortModeInitialized(): Boolean {
        synchronized(sortModeLock) {
            if (prefs.todoManualSortInitialized) return prefs.todoManualSortEnabled
        }
        val inferred = withContext(Dispatchers.IO) { todos.hasManualSortOrder() }
        return synchronized(sortModeLock) {
            // 查询期间用户可能已经拖拽排序；这时以用户的显式选择为准。
            if (!prefs.todoManualSortInitialized) {
                prefs.todoManualSortInitialized = true
                prefs.todoManualSortEnabled = inferred
                todoManualSort.value = inferred
            }
            prefs.todoManualSortEnabled
        }
    }

    fun observeTodos(): Flow<List<TodoEntity>> = flow {
        ensureTodoSortModeInitialized()
        emitAll(
            combine(todos.observeAllRaw(), todoManualSort) { items, manual ->
                TodoSorting.sort(items, manual, DayMath.today())
            }
        )
    }

    fun sortedTodosForWidgetBlocking(limit: Int, showDone: Boolean): List<TodoEntity> {
        synchronized(sortModeLock) {
            if (!prefs.todoManualSortInitialized) {
                val inferred = todos.hasManualSortOrder()
                prefs.todoManualSortInitialized = true
                prefs.todoManualSortEnabled = inferred
                todoManualSort.value = inferred
            }
        }
        val items = if (showDone) {
            todos.getForWidgetIncludingDoneRaw()
        } else {
            todos.getPendingForWidgetRaw()
        }
        return TodoSorting.sort(items, prefs.todoManualSortEnabled, DayMath.today()).take(limit)
    }

    private suspend fun setManualTodoSort(enabled: Boolean) {
        synchronized(sortModeLock) {
            prefs.todoManualSortInitialized = true
            prefs.todoManualSortEnabled = enabled
            todoManualSort.value = enabled
        }
    }

    /** 新建时把模式判断、顺序号分配和插入放在同一串行写入流程中。 */
    suspend fun addTodo(todo: TodoEntity): Long = todoWriteMutex.withLock {
        val manualSort = ensureTodoSortModeInitialized()
        if (manualSort) {
            todos.insertWithNextSortOrder(todo)
        } else {
            todos.insert(todo)
        }
    }

    /** 先原子写完整组顺序，再启用手动模式，避免界面短暂回退到数据库原始顺序。 */
    suspend fun applyManualTodoOrder(ids: List<Long>) = todoWriteMutex.withLock {
        db.withTransaction {
            ids.forEachIndexed { index, id -> todos.setSortOrder(id, index) }
        }
        setManualTodoSort(true)
    }

    companion object {
        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}

package com.glass.todo.data

import android.content.Context
import androidx.room.withTransaction

/**
 * 极简依赖容器。
 *
 * 这个项目只有一个模块、依赖关系也很浅，用手动单例比引入 Hilt 更划算：
 * 少一个注解处理器，构建更快，组件那条链路上也不用处理注入时机问题。
 */
class Repository private constructor(context: Context) {

    private val db = AppDatabase.get(context)
    val prefs = Prefs(context)

    val todos: TodoDao get() = db.todoDao()

    /** 在一个事务里写完整组顺序，避免排序过程中 Room 发出半成品列表。 */
    suspend fun reorderTodos(ids: List<Long>) = db.withTransaction {
        ids.forEachIndexed { index, id -> todos.setSortOrder(id, index) }
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

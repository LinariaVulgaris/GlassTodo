package com.glass.todo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    // ---------- 给 UI 用的响应式查询 ----------

    @Query(
        """
        SELECT * FROM todos
        ORDER BY done ASC,
                 sortOrder ASC,
                 createdAt DESC,
                 id DESC
        """
    )
    fun observeAllRaw(): Flow<List<TodoEntity>>

    // ---------- 给桌面组件用的同步查询（必须在子线程调用） ----------

    @Query(
        """
        SELECT * FROM todos
        WHERE done = 0
        ORDER BY sortOrder ASC,
                 createdAt DESC,
                 id DESC
        """
    )
    fun getPendingForWidgetRaw(): List<TodoEntity>

    @Query("SELECT COUNT(*) FROM todos WHERE done = 0")
    fun countPending(): Int

    /** 设置里打开「显示已完成」时使用，已完成的排在后面 */
    @Query(
        """
        SELECT * FROM todos
        ORDER BY done ASC,
                 sortOrder ASC,
                 createdAt DESC,
                 id DESC
        """
    )
    fun getForWidgetIncludingDoneRaw(): List<TodoEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM todos WHERE sortOrder != 0 LIMIT 1)")
    fun hasManualSortOrder(): Boolean

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM todos")
    fun maxSortOrder(): Int

    @Query("SELECT * FROM todos WHERE id = :id")
    fun getByIdSync(id: Long): TodoEntity?

    // ---------- 写入 ----------

    @Insert
    suspend fun insert(todo: TodoEntity): Long

    /** 撤销删除只恢复缺失记录；即使调用重复也不会覆盖同 ID 的现有内容。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(todo: TodoEntity): Long

    /** 顺序号读取与插入必须在同一事务中，避免并发新建拿到相同权重。 */
    @Transaction
    suspend fun insertWithNextSortOrder(todo: TodoEntity): Long {
        val maxOrder = maxSortOrder()
        check(maxOrder < Int.MAX_VALUE) { "待办顺序号已达上限" }
        return insert(todo.copy(sortOrder = maxOrder + 1))
    }

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)

    /**
     * 原子切换完成状态，返回实际更新的行数。
     *
     * 不用「先查 done 再写 !done」：组件和 App 可能几乎同时点同一条，
     * 两个请求都读到旧值时会写入同一个结果，导致快速连点少切一次。
     */
    @Query("UPDATE todos SET done = CASE done WHEN 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun toggleDone(id: Long): Int

    @Query("UPDATE todos SET done = 0 WHERE done = 1")
    suspend fun clearAllDone()

    @Query("UPDATE todos SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)
}

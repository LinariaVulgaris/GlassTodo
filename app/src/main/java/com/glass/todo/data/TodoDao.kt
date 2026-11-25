package com.glass.todo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    // ---------- 给 UI 用的响应式查询 ----------

    @Query(
        """
        SELECT * FROM todos
        ORDER BY done ASC,
                 CASE WHEN dueEpochDay IS NULL THEN 1 ELSE 0 END ASC,
                 dueEpochDay ASC,
                 CASE WHEN dueMinuteOfDay IS NULL THEN -1 ELSE dueMinuteOfDay END ASC,
                 sortOrder ASC,
                 createdAt DESC
        """
    )
    fun observeAll(): Flow<List<TodoEntity>>

    // ---------- 给桌面组件用的同步查询（必须在子线程调用） ----------

    /** 组件上只显示未完成的待办，带截止日的排在前面 */
    @Query(
        """
        SELECT * FROM todos
        WHERE done = 0
        ORDER BY CASE WHEN dueEpochDay IS NULL THEN 1 ELSE 0 END ASC,
                 dueEpochDay ASC,
                 CASE WHEN dueMinuteOfDay IS NULL THEN -1 ELSE dueMinuteOfDay END ASC,
                 sortOrder ASC,
                 createdAt DESC
        LIMIT :limit
        """
    )
    fun getPendingForWidget(limit: Int): List<TodoEntity>

    @Query("SELECT COUNT(*) FROM todos WHERE done = 0")
    fun countPending(): Int

    /** 设置里打开「显示已完成」时使用，已完成的排在后面 */
    @Query(
        """
        SELECT * FROM todos
        ORDER BY done ASC,
                 CASE WHEN dueEpochDay IS NULL THEN 1 ELSE 0 END ASC,
                 dueEpochDay ASC,
                 CASE WHEN dueMinuteOfDay IS NULL THEN -1 ELSE dueMinuteOfDay END ASC,
                 sortOrder ASC,
                 createdAt DESC
        LIMIT :limit
        """
    )
    fun getForWidgetIncludingDone(limit: Int): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE id = :id")
    fun getByIdSync(id: Long): TodoEntity?

    // ---------- 写入 ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity): Long

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
}

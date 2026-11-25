package com.glass.todo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query(
        """
        SELECT * FROM events
        WHERE archived = 0
        ORDER BY targetEpochDay ASC,
                 CASE WHEN targetMinuteOfDay IS NULL THEN -1 ELSE targetMinuteOfDay END ASC,
                 sortOrder ASC
        """
    )
    fun observeActive(): Flow<List<EventEntity>>

    @Query(
        """
        SELECT * FROM events
        ORDER BY targetEpochDay ASC,
                 CASE WHEN targetMinuteOfDay IS NULL THEN -1 ELSE targetMinuteOfDay END ASC,
                 sortOrder ASC
        """
    )
    fun observeAll(): Flow<List<EventEntity>>

    @Query(
        """
        SELECT * FROM events
        WHERE archived = 0
        ORDER BY targetEpochDay ASC,
                 CASE WHEN targetMinuteOfDay IS NULL THEN -1 ELSE targetMinuteOfDay END ASC,
                 sortOrder ASC
        """
    )
    fun getActiveSync(): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id")
    fun getByIdSync(id: Long): EventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)
}

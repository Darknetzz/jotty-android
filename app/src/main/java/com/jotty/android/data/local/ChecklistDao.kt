package com.jotty.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklists WHERE instanceId = :instanceId AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllChecklistsFlow(instanceId: String): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklists WHERE instanceId = :instanceId AND isDeleted = 0 ORDER BY updatedAt DESC")
    suspend fun getAllChecklists(instanceId: String): List<ChecklistEntity>

    @Query("SELECT * FROM checklists WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: String): ChecklistEntity?

    @Query("SELECT * FROM checklists WHERE instanceId = :instanceId AND (isDirty = 1 OR isDeleted = 1)")
    suspend fun getDirty(instanceId: String): List<ChecklistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ChecklistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ChecklistEntity>)

    @Update
    suspend fun update(entity: ChecklistEntity)

    @Query("UPDATE checklists SET isDeleted = 1, isDirty = 1 WHERE id = :id")
    suspend fun markAsDeleted(id: String)

    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM checklists WHERE instanceId = :instanceId")
    suspend fun deleteAll(instanceId: String)
}

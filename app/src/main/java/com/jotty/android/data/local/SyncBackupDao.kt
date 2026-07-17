package com.jotty.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncBackupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SyncBackupEntity): Long

    @Query(
        """
        SELECT * FROM sync_backups
        WHERE instanceId = :instanceId AND kind = :kind AND itemId = :itemId
        ORDER BY createdAtEpochMs DESC
        """,
    )
    suspend fun listForItem(
        instanceId: String,
        kind: String,
        itemId: String,
    ): List<SyncBackupEntity>

    @Query("SELECT * FROM sync_backups WHERE id = :id")
    suspend fun getById(id: Long): SyncBackupEntity?

    @Query("DELETE FROM sync_backups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_backups WHERE instanceId = :instanceId")
    suspend fun deleteAllForInstance(instanceId: String)
}

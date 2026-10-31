package com.bqsqwad.share.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: String,
    val displayName: String,
    val mime: String,
    val bytes: Long,
    val bytesDone: Long,
    val status: String,
    val peerName: String,
    val localPath: String?,
    val createdAt: Long,
    val favorite: Boolean,
)

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE favorite = 1 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<TransferEntity>>

    @Insert
    suspend fun insert(entity: TransferEntity): Long

    @Update
    suspend fun update(entity: TransferEntity)

    @Query("DELETE FROM transfers")
    suspend fun clear()

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(SUM(bytes),0) FROM transfers WHERE status = 'DONE'")
    fun observeMovedBytes(): Flow<Long>
}

@Database(entities = [TransferEntity::class], version = 1, exportSchema = false)
abstract class ShareDatabase : androidx.room.RoomDatabase() {
    abstract fun transfers(): TransferDao
}

package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatRoomDao {
    @Query("SELECT * FROM chat_rooms ORDER BY createdAt DESC")
    fun getAllRooms(): Flow<List<ChatRoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: ChatRoomEntity)

    @Query("DELETE FROM chat_rooms WHERE roomId = :roomId")
    suspend fun deleteRoom(roomId: String)

    @Query("DELETE FROM chat_rooms")
    suspend fun deleteAllRooms()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    fun getMessagesForRoom(roomId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId")
    suspend fun deleteMessagesForRoom(roomId: String)
}

@Dao
interface ScanLogDao {
    @Query("SELECT * FROM scan_logs ORDER BY timestamp DESC")
    fun getScanLogs(): Flow<List<ScanLogEntity>>

    @Query("SELECT * FROM scan_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastScanLog(): ScanLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanLog(log: ScanLogEntity)
}

@Database(
    entities = [ChatRoomEntity::class, ChatMessageEntity::class, ScanLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatRoomDao(): ChatRoomDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun scanLogDao(): ScanLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "battery_buddies_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

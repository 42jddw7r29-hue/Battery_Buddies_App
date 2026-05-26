package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_rooms")
data class ChatRoomEntity(
    @PrimaryKey val roomId: String,
    val roomNameAr: String,
    val roomNameEn: String,
    val roomType: String, // "BATTERY", "PROCESSOR", "SCREEN"
    val matchValue: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomId: String,
    val senderName: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isUser: Boolean
)

@Entity(tableName = "scan_logs")
data class ScanLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val batteryPct: Int,
    val cpuInfo: String,
    val screenGrade: String, // "A" (Perfect), "B" (Unstable), "C" (Severely Cracked)
    val timestamp: Long = System.currentTimeMillis()
)

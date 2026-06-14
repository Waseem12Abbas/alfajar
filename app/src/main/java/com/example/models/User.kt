package com.example.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val pin_hash: String, // SHA-256 of PIN
    val role: String, // 'admin' or 'scanner'
    val created_at: String
)

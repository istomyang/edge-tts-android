package com.istomyang.edgetss.data

import android.content.Context
import androidx.room.Room
import com.istomyang.edgetss.data.database.Log
import com.istomyang.edgetss.data.database.LogDao
import com.istomyang.edgetss.data.database.LogDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneOffset

class LogRepository(val dataSource: LogDataSource, val disabled: Boolean) {
    suspend fun insert(domain: String, level: LogLevel, message: String) {
        if (disabled) {
            return
        }
        val log = Log(
            domain = domain,
            level = level.name,
            message = message,
            createdAt = timestampBefore(0, 0, 0)
        )
        dataSource.dao.inert(log)
    }

    fun info(domain: String, message: String) = CoroutineScope(Dispatchers.IO).launch {
        insert(domain = domain, level = LogLevel.INFO, message = message)
    }

    fun debug(domain: String, message: String) = CoroutineScope(Dispatchers.IO).launch {
        insert(domain = domain, level = LogLevel.DEBUG, message = message)
    }

    fun error(domain: String, message: String) = CoroutineScope(Dispatchers.IO).launch {
        insert(domain = domain, level = LogLevel.ERROR, message = message)
    }

    fun warn(domain: String, message: String) = CoroutineScope(Dispatchers.IO).launch {
        insert(domain = domain, level = LogLevel.WARNING, message = message)
    }

    suspend fun query(
        domain: String = "",
        level: LogLevel = LogLevel.INFO,
        afterAt: Long = timestampBefore(0, 0, 1),
        o: Int,
        l: Int
    ): List<Log> {
        return dataSource.dao.query(
            domain, level.name, afterAt, o, l
        )
    }

    suspend fun delete(
        domain: String = "",
        level: String = "",
        beforeAt: Long
    ) {
        dataSource.dao.delete(domain, level, beforeAt)
    }

    companion object {
        fun create(context: Context, disabled: Boolean = false): LogRepository {
            val db = Room.databaseBuilder(
                context,
                LogDatabase::class.java,
                "log"
            ).build()
            return LogRepository(LogDataSource(db.logDao()), disabled)
        }

        fun timestampBefore(min: Long, hour: Long, day: Long): Long {
            val now = LocalDateTime.now()
            val targetDateTime = now.minusMinutes(min).minusHours(hour).minusDays(day)
            return targetDateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        }
    }
}

class LogDataSource(val dao: LogDao)

enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}
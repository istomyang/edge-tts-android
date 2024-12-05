package com.istomyang.edgetss.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase


@Database(entities = [Log::class], version = 1, exportSchema = false)
abstract class LogDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
}

@Dao
interface LogDao {
    @Insert
    suspend fun insertBatch(logs: List<Log>)

    @Insert
    suspend fun inert(log: Log)

    @Query(
        """
        SELECT * FROM log
        WHERE (CASE WHEN :domain = '' THEN 1 = 1 ELSE domain = :domain END)
              AND (CASE WHEN :level = '' THEN 1 = 1 ELSE level = :level END)
              AND (CASE WHEN :afterAt = 0 THEN 1 = 1 ELSE created_at > :afterAt END)
        LIMIT :z OFFSET (:p - 1) * :z
    """
    )
    suspend fun query(
        domain: String = "",
        level: String = "",
        afterAt: Long = 0,
        p: Int = 1,
        z: Int = 200
    ): List<Log>

    @Query(
        """
        DELETE FROM log
        WHERE (CASE WHEN :domain = '' THEN 1 = 1 ELSE domain = :domain END)
              AND (CASE WHEN :level = '' THEN 1 = 1 ELSE level = :level END)
              AND created_at < :beforeAt
    """
    )
    suspend fun delete(
        domain: String = "",
        level: String = "",
        beforeAt: Long
    )
}

@Entity(tableName = "log", indices = [])
data class Log(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "domain") val domain: String,
    @ColumnInfo(name = "level") val level: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "created_at") val createdAt: Long // in ms
)
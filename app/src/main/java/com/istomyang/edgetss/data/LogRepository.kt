package com.istomyang.edgetss.data

import android.content.Context
import androidx.annotation.GuardedBy
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.reflect.KProperty

val Context.repositoryLog by LogRepositoryDelegate()

class LogRepository(
    private val localDataSource: LogLocalDataSource,
    private val preferenceDataSource: DataStore<Preferences>
) {
    val enabled = preferenceDataSource.data.map { it[KEY_ENABLED] == true }

    suspend fun open(enabled: Boolean = true) {
        preferenceDataSource.edit {
            it[KEY_ENABLED] = enabled
        }
    }

    fun insert(domain: String, level: LogLevel, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!enabled.first()) {
                return@launch
            }
            val log = Log(
                domain = domain,
                level = level.name,
                message = message,
                createdAt = timestampBefore(0, 0, 0)
            )
            localDataSource.dao.inert(log)
        }
    }

    fun info(domain: String, message: String) = insert(domain = domain, level = LogLevel.INFO, message = message)

    fun debug(domain: String, message: String) = insert(domain = domain, level = LogLevel.DEBUG, message = message)

    fun error(domain: String, message: String) = insert(domain = domain, level = LogLevel.ERROR, message = message)

    fun warn(domain: String, message: String) = insert(domain = domain, level = LogLevel.WARNING, message = message)

    suspend fun query(o: Int, l: Int) = localDataSource.dao.query(o, l)

    suspend fun clear() {
        localDataSource.dao.clear()
    }

    companion object {
        fun create(context: Context): LogRepository {
            val db = Room.databaseBuilder(
                context,
                LogDatabase::class.java,
                "log"
            ).build()
            val localDS = LogLocalDataSource(db.logDao())
            val preferenceDS = context.dateStoreLog
            return LogRepository(localDS, preferenceDS)
        }

        fun timestampBefore(min: Long, hour: Long, day: Long): Long {
            val now = LocalDateTime.now()
            val targetDateTime = now.minusMinutes(min).minusHours(hour).minusDays(day)
            return targetDateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        }

        private val KEY_ENABLED = booleanPreferencesKey("enabled")
    }
}

private val Context.dateStoreLog by preferencesDataStore("log")

class LogRepositoryDelegate {
    private val lock = Any()

    @GuardedBy("lock")
    @Volatile
    private var instance: LogRepository? = null

    operator fun getValue(thisRef: Context, property: KProperty<*>): LogRepository {
        return instance ?: synchronized(lock) {
            if (instance == null) {
                val applicationContext = thisRef.applicationContext
                instance = LogRepository.create(applicationContext)
            }
            instance!!
        }
    }
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

// region LogDataSource

class LogLocalDataSource(val dao: LogDao)

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

    @Query("SELECT * FROM log ORDER BY created_at ASC LIMIT :l OFFSET :o")
    suspend fun query(
        o: Int = 0,
        l: Int = 100
    ): List<Log>

    @Query("DELETE FROM log")
    suspend fun clear()
}

@Entity(tableName = "log", indices = [])
data class Log(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "domain") val domain: String,
    @ColumnInfo(name = "level") val level: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "created_at") val createdAt: Long // in ms
)

// endregion
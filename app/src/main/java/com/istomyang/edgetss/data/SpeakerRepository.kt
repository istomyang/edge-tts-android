package com.istomyang.edgetss.data

import android.content.Context
import androidx.annotation.GuardedBy
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.istomyang.edgetss.engine.listVoices
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KProperty

val Context.repositorySpeaker by SpeakerRepositoryDelegate()

class SpeakerRepository(
    private val localDS: SpeakerLocalDataSource,
    private val remoteDS: SpeakerRemoteDataSource,
) {
    private lateinit var voices: List<Voice>

    suspend fun fetchAll(): Result<List<Voice>> {
        if (::voices.isInitialized) {
            return Result.success(voices)
        }
        val result = remoteDS.getAll()
        if (result.isSuccess) {
            voices = result.getOrNull()!!
            return Result.success(voices)
        }
        return Result.failure(result.exceptionOrNull()!!)
    }

    suspend fun get(id: String): Voice? {
        return localDS.dao.get(id)
    }

    fun getActiveFlow(): Flow<Voice?> {
        return localDS.dao.getActiveFlow()
    }

    suspend fun removeActive() {
        localDS.dao.removeActive()
    }

    suspend fun setActive(id: String) {
        localDS.dao.setActive(id)
    }

    suspend fun getActive(): Voice? {
        return localDS.dao.getActive()
    }

    fun getFlow(): Flow<List<Voice>> {
        return localDS.dao.getFlow()
    }

    suspend fun insert(ids: Set<String>) {
        if (!::voices.isInitialized) {
            return
        }
        val save = voices.filter { it.uid in ids }
        localDS.dao.inserts(save)
    }

    suspend fun delete(ids: Set<String>) {
        localDS.dao.delete(ids)
    }

    companion object {
        fun create(context: Context): SpeakerRepository {
            val db = Room.databaseBuilder(
                context,
                VoiceDatabase::class.java,
                "voice"
            ).build()
            val localDS = SpeakerLocalDataSource(db.voiceDao())
            val remoteDS = SpeakerRemoteDataSource()
            return SpeakerRepository(localDS, remoteDS)
        }
    }
}

class SpeakerRepositoryDelegate {
    private val lock = Any()

    @GuardedBy("lock")
    @Volatile
    private var instance: SpeakerRepository? = null

    operator fun getValue(thisRef: Context, property: KProperty<*>): SpeakerRepository {
        return instance ?: synchronized(lock) {
            if (instance == null) {
                val applicationContext = thisRef.applicationContext
                instance = SpeakerRepository.create(applicationContext)
            }
            instance!!
        }
    }
}

// region SpeakerRemoteDataSource

class SpeakerRemoteDataSource {
    suspend fun getAll(): Result<List<Voice>> {
        listVoices().onSuccess { items ->
            val ret = items.map { it ->
                Voice(
                    uid = it.name,
                    name = it.name,
                    shortName = it.shortName,
                    gender = it.gender,
                    locale = it.locale,
                    suggestedCodec = it.suggestedCodec,
                    friendlyName = it.friendlyName,
                    status = it.status,
                    contentCategories = it.voiceTag.contentCategories.joinToString(", "),
                    voicePersonalities = it.voiceTag.voicePersonalities.joinToString(", "),
                    active = false
                )
            }
            return Result.success(ret)
        }.onFailure {
            return Result.failure(it)
        }

        return Result.failure(Throwable("error"))
    }
}

// endregion

// region SpeakerLocalDataSource

class SpeakerLocalDataSource(val dao: VoiceDao)

@Database(entities = [Voice::class], version = 1, exportSchema = false)
abstract class VoiceDatabase : RoomDatabase() {
    abstract fun voiceDao(): VoiceDao
}

@Dao
interface VoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserts(voice: List<Voice>)

    @Query("SELECT * FROM voice WHERE uid = :id")
    suspend fun get(id: String): Voice?

    @Query("SELECT * FROM voice WHERE uid IN (:ids)")
    suspend fun getByIds(ids: Set<String>): List<Voice>

    @Query("SELECT * FROM voice")
    suspend fun getAll(): List<Voice>

    @Query("DELETE FROM voice WHERE uid IN (:ids)")
    suspend fun delete(ids: Set<String>)

    @Query("SELECT * FROM voice")
    fun getFlow(): Flow<List<Voice>>

    @Query("SELECT * FROM voice WHERE active = 1")
    fun getActiveFlow(): Flow<Voice?>

    @Query("SELECT * FROM voice WHERE active = 1")
    suspend fun getActive(): Voice?

    @Query("UPDATE voice SET active = 1 WHERE uid = :id")
    suspend fun setActive(id: String)

    @Query("UPDATE voice SET active = 0 WHERE active = 1")
    suspend fun removeActive()
}

@Entity(tableName = "voice", indices = [Index("locale")])
data class Voice(
    @PrimaryKey val uid: String,
    @ColumnInfo(name = "active") val active: Boolean,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "short_name") val shortName: String,
    @ColumnInfo(name = "gender") val gender: String,
    @ColumnInfo(name = "locale") val locale: String,
    @ColumnInfo(name = "suggested_codec") val suggestedCodec: String,
    @ColumnInfo(name = "friendly_name") val friendlyName: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "content_categories") val contentCategories: String, // split by ,
    @ColumnInfo(name = "voice_personalities") val voicePersonalities: String,
)

// endregion
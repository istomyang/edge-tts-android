package com.istomyang.edgetss.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase


@Database(entities = [Voice::class], version = 1, exportSchema = false)
abstract class VoiceDatabase : RoomDatabase() {
    abstract fun voiceDao(): VoiceDao
}

@Dao
interface VoiceDao {
    @Insert
    suspend fun insertAll(voice: List<Voice>)

    @Query("SELECT * FROM voice WHERE uid = :id")
    suspend fun get(id: Int): Voice?

    @Query("SELECT COUNT(*) == 0 FROM voice")
    suspend fun isEmpty(): Boolean

    @Query("SELECT * FROM voice WHERE uid IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<Voice>

    @Query("SELECT * FROM voice WHERE locale = :locale")
    suspend fun query(locale: String): List<Voice>

    @Query("SELECT DISTINCT locale FROM voice")
    suspend fun listLocales(): List<String>
}

@Entity(tableName = "voice", indices = [Index("gender"), Index("locale")])
data class Voice(
    @PrimaryKey val uid: Int,
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

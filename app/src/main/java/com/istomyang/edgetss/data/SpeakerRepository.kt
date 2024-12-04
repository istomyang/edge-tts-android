package com.istomyang.edgetss.data

import android.content.Context
import androidx.room.Room
import com.istomyang.edgetss.data.database.Voice
import com.istomyang.edgetss.data.database.VoiceDao
import com.istomyang.edgetss.data.database.VoiceDatabase
import com.istomyang.edgetss.engine.listVoices

class SpeakerRepository(
    private val localDS: SpeakerLocalDataSource,
    private val remoteDS: SpeakerRemoteDataSource
) {
    suspend fun getByIds(ids: List<Int>): List<Voice> {
        return localDS.dao.getByIds(ids)
    }

    suspend fun getById(id: Int): Voice? {
        return localDS.dao.get(id)
    }

    suspend fun query(locale: String): List<Voice> {
        return localDS.dao.query(locale)
    }

    suspend fun queryLocales(): List<String> {
        return localDS.dao.listLocales()
    }

    suspend fun ensureLocalInitialized(): Result<Unit> {
        if (!localDS.dao.isEmpty()) {
            return Result.success(Unit)
        }

        remoteDS.getAll().onSuccess {
            localDS.dao.insertAll(it)
            return Result.success(Unit)
        }.onFailure {
            return Result.failure(it)
        }
        return Result.failure(Throwable("error"))
    }

    companion object {
        fun create(context: Context): SpeakerRepository {
            val db = Room.databaseBuilder(
                context,
                VoiceDatabase::class.java,
                "voice"
            ).build()
            val localDS = SpeakerLocalDataSource(db, db.voiceDao())
            val remoteDS = SpeakerRemoteDataSource()

            return SpeakerRepository(localDS, remoteDS)
        }
    }
}

class SpeakerRemoteDataSource {
    suspend fun getAll(): Result<List<Voice>> {
        listVoices().onSuccess { items ->
            val ret = items.mapIndexed { index, it ->
                Voice(
                    name = it.name,
                    shortName = it.shortName,
                    gender = it.gender,
                    locale = it.locale,
                    suggestedCodec = it.suggestedCodec,
                    friendlyName = it.friendlyName,
                    status = it.status,
                    uid = index,
                    contentCategories = it.voiceTag.contentCategories.joinToString(", "),
                    voicePersonalities = it.voiceTag.voicePersonalities.joinToString(", ")
                )
            }
            return Result.success(ret)
        }.onFailure {
            return Result.failure(it)
        }

        return Result.failure(Throwable("error"))
    }
}

class SpeakerLocalDataSource(private val db: VoiceDatabase, val dao: VoiceDao)

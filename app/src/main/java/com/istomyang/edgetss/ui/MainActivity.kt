package com.istomyang.edgetss.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.istomyang.edgetss.data.LogRepository
import com.istomyang.edgetss.data.PreferenceRepository
import com.istomyang.edgetss.ui.main.MainContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runTask()

        enableEdgeToEdge()
        setContent {
            MainContent()
        }
    }

    fun runTask() {
        CoroutineScope(Dispatchers.IO).launch {
            cleanLogRegularly()
        }
    }

    private suspend fun cleanLogRegularly() {
        val preference = PreferenceRepository.create(this)
        val log = LogRepository.create(this)
        val day = preference.logSaveTime.first()
        if (day == null) {
            return
        }
        val ts = LogRepository.timestampBefore(0, 0, day.toLong())
        log.delete(beforeAt = ts)
    }
}




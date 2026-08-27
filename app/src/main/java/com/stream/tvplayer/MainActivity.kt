package com.dk.tvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.stream.tvplayer.ui.TvPlayerViewModel
import com.stream.tvplayer.ui.screens.TvMainScreen

class MainActivity : ComponentActivity() {

    private val viewModel: TvPlayerViewModel by viewModels()

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load Remote Playlist & XMLTV Guide into Room DB on startup
        viewModel.syncFeeds(
            m3uUrl = "https://iptv-org.github.io/iptv/countries/in.m3u",
            epgUrl = "https://iptv-org.github.io/epg/guides/in/airtel.in.xml"
        )

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TvMainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

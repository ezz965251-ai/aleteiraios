package com.nexus.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.music.core.ArtistTitle
import com.nexus.music.data.stream.PlaylistLinks

/**
 * Стартовый экран iOS-версии Aletheia. Это проверка, что общий Kotlin-код
 * (разбор «артист — название», ссылки плейлистов) работает на iPhone.
 * Остальные экраны переносятся сюда из Android-версии поэтапно (см. MIGRATION.md).
 */
@Composable
fun App() {
    MaterialTheme {
        var title by remember { mutableStateOf("Artist1, Artist2 - Song name (feat. X)") }
        var link by remember { mutableStateOf("https://music.yandex.ru/users/name/playlists/1000") }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Aletheia (iOS)", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Название трека") })
            Text("Разбор: ${ArtistTitle.split(title)}")
            OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(), label = { Text("Ссылка на плейлист") })
            Text("Нормализовано: ${PlaylistLinks.normalize(link)}")
        }
    }
}

package com.lostf1sh.pixelplayeross.presentation.selection

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import com.lostf1sh.pixelplayeross.data.model.Song
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet

@Stable
internal class DetailSongSelection<K> {
    var keys by mutableStateOf(persistentSetOf<K>(), referentialEqualityPolicy())
        private set

    val isActive by derivedStateOf(structuralEqualityPolicy()) { keys.isNotEmpty() }

    fun toggle(key: K) {
        keys = if (key in keys) keys.remove(key) else keys.add(key)
    }

    fun clear() {
        keys = persistentSetOf()
    }

    fun selectAll(visibleKeys: Iterable<K>) {
        keys = visibleKeys.toPersistentSet()
    }

    fun rangeIn(visibleKeys: List<K>): List<K>? {
        val first = visibleKeys.indexOfFirst { it in keys }
        val last = visibleKeys.indexOfLast { it in keys }
        return if (first >= 0 && last > first) visibleKeys.subList(first, last + 1) else null
    }

    fun keepVisible(visibleKeys: Set<K>) {
        if (!visibleKeys.containsAll(keys)) {
            keys = keys.filter { it in visibleKeys }.toPersistentSet()
        }
    }

    fun songsIn(visibleSongs: Map<K, Song>) = keys.mapNotNull(visibleSongs::get).toImmutableList()

    fun completionCallback(): () -> Unit {
        val snapshot = keys
        return { if (keys === snapshot) clear() }
    }
}

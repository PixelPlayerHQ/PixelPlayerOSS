package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.database.EngagementDao
import com.lostf1sh.pixelplayeross.data.database.SongEngagementEntity
import com.lostf1sh.pixelplayeross.data.preferences.MostListenedType
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MostListenedViewModel @Inject constructor(
    engagementDao: EngagementDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val engagements: StateFlow<List<SongEngagementEntity>> = engagementDao.getAllEngagementsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostListenedType: StateFlow<MostListenedType> = userPreferencesRepository.mostListenedTypeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MostListenedType.SONGS)

    fun setMostListenedType(type: MostListenedType) {
        viewModelScope.launch {
            userPreferencesRepository.setMostListenedType(type)
        }
    }
}

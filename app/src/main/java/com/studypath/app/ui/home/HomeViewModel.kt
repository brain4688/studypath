package com.studypath.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studypath.app.data.repo.PlanCard
import com.studypath.app.data.repo.PlanRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repository: PlanRepository) : ViewModel() {
    val plans = repository.observePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultConfig = repository.observeDefaultConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

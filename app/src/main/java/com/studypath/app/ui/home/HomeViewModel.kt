package com.studypath.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studypath.app.core.PlanParser
import com.studypath.app.data.repo.PlanCard
import com.studypath.app.data.repo.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 导入计划的状态 */
sealed interface ImportState {
    data object Idle : ImportState
    data class Success(val planId: Long) : ImportState
    data class Error(val message: String) : ImportState
}

class HomeViewModel(private val repository: PlanRepository) : ViewModel() {
    val plans = repository.observePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultConfig = repository.observeDefaultConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState

    /** 导入 JSON 格式的学习计划（App 导出的 JSON 或按格式示例手写的 JSON） */
    fun importPlan(text: String) {
        if (text.isBlank()) {
            _importState.value = ImportState.Error("请先粘贴 JSON 或选择文件")
            return
        }
        viewModelScope.launch {
            runCatching { PlanParser.parse(text) }.fold(
                onSuccess = { aiPlan ->
                    val planId = repository.createPlan(aiPlan, aiPlan.title.ifBlank { "导入的计划" }, "导入")
                    _importState.value = ImportState.Success(planId)
                },
                onFailure = { e ->
                    _importState.value = ImportState.Error("解析失败：${e.message}")
                },
            )
        }
    }

    fun consumeImportState() {
        if (_importState.value != ImportState.Idle) _importState.value = ImportState.Idle
    }
}

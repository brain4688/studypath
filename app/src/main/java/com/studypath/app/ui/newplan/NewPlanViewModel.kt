package com.studypath.app.ui.newplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studypath.app.core.PlanParser
import com.studypath.app.core.ai.LearningRequest
import com.studypath.app.core.ai.PlanPrompts
import com.studypath.app.data.api.AiClient
import com.studypath.app.data.repo.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 生成中的 UI 状态 */
sealed interface GenerateState {
    data object Idle : GenerateState
    data object Loading : GenerateState
    data class Success(val planId: Long) : GenerateState
    data class Error(val message: String) : GenerateState
}

class NewPlanViewModel(
    private val repository: PlanRepository,
    private val aiClient: AiClient,
) : ViewModel() {

    val configs = repository.observeConfigs()

    private val _state = MutableStateFlow<GenerateState>(GenerateState.Idle)
    val state: StateFlow<GenerateState> = _state

    fun generate(
        configId: Long,
        topic: String,
        level: String,
        dailyMinutes: Int,
        deadlineWeeks: Int,
        purpose: String,
        notes: String,
    ) {
        if (_state.value is GenerateState.Loading) return
        if (topic.isBlank()) {
            _state.value = GenerateState.Error("请先填写你想学习的内容")
            return
        }
        _state.value = GenerateState.Loading
        viewModelScope.launch {
            val config = repository.observeConfigs().first().firstOrNull { it.id == configId }
                ?: run {
                    _state.value = GenerateState.Error("未找到模型配置，请先在设置中添加")
                    return@launch
                }
            val request = LearningRequest(
                topic = topic.trim(), level = level.trim(),
                dailyMinutes = dailyMinutes, deadlineWeeks = deadlineWeeks,
                purpose = purpose, notes = notes.trim(),
            )
            val result = aiClient.complete(config, PlanPrompts.SYSTEM, PlanPrompts.newUserPrompt(request))
            result.fold(
                onSuccess = { content ->
                    runCatching { PlanParser.parse(content) }.fold(
                        onSuccess = { aiPlan ->
                            val planId = repository.createPlan(aiPlan, request.topic, "${config.name} · ${config.model}")
                            _state.value = GenerateState.Success(planId)
                        },
                        onFailure = { e ->
                            _state.value = GenerateState.Error("模型返回内容无法解析为计划：${e.message}")
                        },
                    )
                },
                onFailure = { e ->
                    _state.value = GenerateState.Error("请求失败：${e.message}")
                },
            )
        }
    }

    fun consumeError() {
        if (_state.value is GenerateState.Error) _state.value = GenerateState.Idle
    }
}

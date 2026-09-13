package com.studypath.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studypath.app.core.PlanParser
import com.studypath.app.core.ai.PlanPrompts
import com.studypath.app.data.api.AiClient
import com.studypath.app.data.api.ChatMessage
import com.studypath.app.data.db.ChatMessageEntity
import com.studypath.app.data.repo.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 聊天页状态 */
sealed interface ChatUiState {
    /** 顾问聊天中 */
    data object Chatting : ChatUiState

    /** 正在根据聊天记录生成计划 */
    data object Planning : ChatUiState

    /** 计划已生成，等待用户预览确认（尚未落库） */
    data class Preview(
        val plan: com.studypath.app.core.ai.AiPlan,
        val configName: String,
        val goal: String,
    ) : ChatUiState

    /** 计划已确认创建 */
    data class PlanReady(val planId: Long) : ChatUiState

    /** 出错 */
    data class Error(val message: String) : ChatUiState
}

class ChatViewModel(
    private val repository: PlanRepository,
    private val aiClient: AiClient,
) : ViewModel() {

    val messages = repository.observeChat()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow<ChatUiState>(ChatUiState.Chatting)
    val state: StateFlow<ChatUiState> = _state

    /** 发送消息：先落库，再带着全部历史请求顾问回复 */
    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty() || _state.value is ChatUiState.Planning) return
        viewModelScope.launch {
            repository.addChatMessage("user", content)
            val history = buildApiMessages(repository.observeChat().first())
            val config = repository.observeDefaultConfig().first()
            if (config == null) {
                _state.value = ChatUiState.Error("尚未配置模型 API，请先到「设置」添加")
                return@launch
            }
            aiClient.complete(config, history).fold(
                onSuccess = { reply ->
                    repository.addChatMessage("assistant", reply.trim())
                    _state.value = ChatUiState.Chatting
                },
                onFailure = { e ->
                    _state.value = ChatUiState.Error("发送失败：${e.message}")
                },
            )
        }
    }

    /** 根据聊天记录生成结构化学习计划 */
    fun generatePlan() {
        if (_state.value is ChatUiState.Planning) return
        viewModelScope.launch {
            _state.value = ChatUiState.Planning
            val history = repository.observeChat().first()
            if (history.none { it.role == "user" }) {
                _state.value = ChatUiState.Error("请先和 AI 聊聊你的学习需求")
                return@launch
            }
            val config = repository.observeDefaultConfig().first()
            if (config == null) {
                _state.value = ChatUiState.Error("尚未配置模型 API，请先到「设置」添加")
                return@launch
            }
            val transcript = history.joinToString("\n") {
                (if (it.role == "user") "我：" else "顾问：") + it.content
            }
            val result = aiClient.complete(
                config,
                PlanPrompts.SYSTEM,
                PlanPrompts.planFromTranscript(transcript),
            )
            result.fold(
                onSuccess = { content ->
                    runCatching { PlanParser.parse(content) }.fold(
                        onSuccess = { aiPlan ->
                            val goal = history.firstOrNull { it.role == "user" }
                                ?.content?.take(80) ?: "我的学习计划"
                            // 不直接落库：先让用户预览确认
                            _state.value = ChatUiState.Preview(
                                plan = aiPlan,
                                configName = "${config.name} · ${config.model}",
                                goal = goal,
                            )
                        },
                        onFailure = { e ->
                            _state.value = ChatUiState.Error("模型返回内容无法解析为计划：${e.message}")
                        },
                    )
                },
                onFailure = { e ->
                    _state.value = ChatUiState.Error("生成失败：${e.message}")
                },
            )
        }
    }

    /** 用户在预览中确认，正式创建计划 */
    fun confirmPreview() {
        val s = _state.value as? ChatUiState.Preview ?: return
        viewModelScope.launch {
            val planId = repository.createPlan(s.plan, s.goal, s.configName)
            _state.value = ChatUiState.PlanReady(planId)
        }
    }

    /** 用户放弃预览，回到聊天继续沟通 */
    fun dismissPreview() {
        if (_state.value is ChatUiState.Preview) _state.value = ChatUiState.Chatting
    }

    /** 开启新对话（清空聊天记录） */
    fun newConversation() {
        viewModelScope.launch {
            repository.clearChat()
            _state.value = ChatUiState.Chatting
        }
    }

    fun consumeState() {
        if (_state.value is ChatUiState.PlanReady || _state.value is ChatUiState.Error) {
            _state.value = ChatUiState.Chatting
        }
    }

    private fun buildApiMessages(history: List<ChatMessageEntity>): List<ChatMessage> =
        listOf(ChatMessage("system", PlanPrompts.CHAT_SYSTEM)) +
            history.map { ChatMessage(it.role, it.content) }
}

package com.studypath.app.data.api

import kotlinx.serialization.Serializable

/** OpenAI 兼容 chat/completions 协议 DTO */

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.5,
)

@Serializable
data class ChatResponse(
    val choices: List<Choice> = emptyList(),
    val error: ApiError? = null,
) {
    @Serializable
    data class Choice(val message: ChatMessage? = null)

    @Serializable
    data class ApiError(val message: String? = null)
}

interface OpenAiCompatibleApi {
    @retrofit2.http.POST("chat/completions")
    suspend fun chat(
        @retrofit2.http.Header("Authorization") authorization: String,
        @retrofit2.http.Body request: ChatRequest,
    ): ChatResponse
}

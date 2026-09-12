package com.studypath.app.data.api

import com.studypath.app.data.db.ApiConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 大模型客户端：任何 OpenAI 兼容的 /chat/completions 接口都可接入。
 * 输入 apiKey 与 baseUrl 由用户在设置页配置（BYOK，密钥仅存本地数据库）。
 */
class AiClient {

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS) // 生成完整计划可能较慢
        .build()

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    // 按 baseUrl 缓存 Retrofit 实例
    private val apis = mutableMapOf<String, OpenAiCompatibleApi>()

    private fun apiFor(baseUrl: String): OpenAiCompatibleApi =
        apis.getOrPut(baseUrl) {
            val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            Retrofit.Builder()
                .baseUrl(normalized)
                .client(okHttp)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(OpenAiCompatibleApi::class.java)
        }

    /** 发送一轮对话，返回模型输出的文本内容 */
    suspend fun complete(config: ApiConfigEntity, system: String, user: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = apiFor(config.baseUrl).chat(
                    authorization = "Bearer ${config.apiKey.trim()}",
                    request = ChatRequest(
                        model = config.model,
                        messages = listOf(
                            ChatMessage("system", system),
                            ChatMessage("user", user),
                        ),
                    ),
                )
                resp.error?.message?.let { error(it) }
                val content = resp.choices.firstOrNull()?.message?.content
                    ?: error("模型未返回内容（choices 为空，请检查模型名称是否正确）")
                content
            }
        }

    /** 连接测试 */
    suspend fun testConnection(config: ApiConfigEntity): Result<String> =
        complete(config, "你是连接测试助手。", "请只回复两个字：成功")
}

package com.studypath.app.data.api

/** 常见模型服务商预设（均为 OpenAI 兼容接口），用户也可在设置页自定义 */
data class ProviderPreset(
    val display: String,
    val baseUrl: String,
    val defaultModel: String,
    val keyUrl: String, // 获取 API Key 的地址
)

object ProviderPresets {
    val all = listOf(
        ProviderPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "https://platform.deepseek.com"),
        ProviderPreset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "https://open.bigmodel.cn"),
        ProviderPreset("Kimi (月之暗面)", "https://api.moonshot.cn/v1", "moonshot-v1-8k", "https://platform.moonshot.cn"),
        ProviderPreset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "https://dashscope.console.aliyun.com"),
        ProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "https://platform.openai.com"),
        ProviderPreset("Groq", "https://api.groq.com/openai/v1", "llama-3.1-8b-instant", "https://console.groq.com"),
        ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "https://openrouter.ai"),
        ProviderPreset("自定义", "", "", ""),
    )
}

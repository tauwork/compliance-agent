package com.pcmvp.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** DeepSeek客户端配置（OpenAI兼容接口） */
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /** DeepSeek API地址（OpenAI兼容），换模型只改此处 */
    private String baseUrl = "https://api.deepseek.com";

    private String apiKey = "";

    /** 主力模型（拆解/归因/问答） */
    private String model = "deepseek-chat";

    /** 复杂推理模型（可选，复杂拆解步骤切换） */
    private String reasonerModel = "deepseek-reasoner";

    /** 超时（秒） */
    private int timeoutSeconds = 60;

    /** 演示降级模式：API不可用时回放缓存结果（现场演示保障，核心判定链路不依赖LLM） */
    private boolean demoFallback = true;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = v; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String v) { this.apiKey = v; }
    public String getModel() { return model; }
    public void setModel(String v) { this.model = v; }
    public String getReasonerModel() { return reasonerModel; }
    public void setReasonerModel(String v) { this.reasonerModel = v; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int v) { this.timeoutSeconds = v; }
    public boolean isDemoFallback() { return demoFallback; }
    public void setDemoFallback(boolean v) { this.demoFallback = v; }
}

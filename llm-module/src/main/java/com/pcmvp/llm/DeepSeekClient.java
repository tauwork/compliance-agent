package com.pcmvp.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * DeepSeek统一客户端（OpenAI兼容 /chat/completions）：
 * 密钥集中持有、手机号/身份证脱敏前置、超时控制、调用全留痕。
 * 演示降级：API不可用且demoFallback开启时返回提示（核心判定链路不依赖LLM，不受影响）。
 */
@Service
@EnableConfigurationProperties(LlmProperties.class)
public class DeepSeekClient {

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern IDCARD = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");

    private final LlmProperties props;
    private final LlmCallLogMapper logMapper;
    private final ObjectMapper om = new ObjectMapper();
    private final RestClient rest;

    public DeepSeekClient(LlmProperties props, LlmCallLogMapper logMapper) {
        this.props = props;
        this.logMapper = logMapper;
        this.rest = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
    }

    /** 是否已配置API Key（未配置时各调用方走确定性降级路径） */
    public boolean isConfigured() {
        return props.getApiKey() != null && !props.getApiKey().isBlank();
    }

    /** 单轮对话（system+user），返回assistant文本 */
    public String chat(String purpose, String system, String user) {
        return chat(purpose, system, user, false);
    }

    /** useReasoner=true 时切 deepseek-reasoner */
    public String chat(String purpose, String system, String user, boolean useReasoner) {
        String model = useReasoner ? props.getReasonerModel() : props.getModel();
        String maskedUser = mask(user == null ? "" : user);
        long t0 = System.currentTimeMillis();
        String response;
        try {
            ObjectNode body = om.createObjectNode();
            body.put("model", model);
            ArrayNode messages = body.putArray("messages");
            if (system != null && !system.isBlank()) {
                ObjectNode m = messages.addObject();
                m.put("role", "system");
                m.put("content", system);
            }
            ObjectNode m = messages.addObject();
            m.put("role", "user");
            m.put("content", maskedUser);
            body.put("stream", false);

            JsonNode resp;
            {
                // 以字节接收再由Jackson解析：DeepSeek响应的content-type不带charset，
                // 直接按String/JsonNode转换会走默认字符集导致中文乱码；byte[]无此问题
                byte[] raw = rest.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + props.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(byte[].class);
                resp = raw == null ? null : om.readTree(raw);
            }
            response = resp == null ? "" : sanitize(resp.path("choices").path(0).path("message").path("content").asText(""));
        } catch (Exception e) {
            if (props.isDemoFallback()) {
                response = "[LLM_UNAVAILABLE] DeepSeek API不可用（演示降级模式）。核心合规判定链路不依赖LLM，不受影响。原因: "
                        + e.getMessage();
            } else {
                throw new IllegalStateException("DeepSeek调用失败: " + e.getMessage(), e);
            }
        }
        saveLog(model, purpose, maskedUser, response, System.currentTimeMillis() - t0);
        return response;
    }

    private void saveLog(String model, String purpose, String promptMasked, String response, long latencyMs) {
        try {
            LlmCallLog log = new LlmCallLog();
            log.setModel(model);
            log.setPurpose(purpose);
            log.setPromptMasked(truncate(promptMasked, 8000));
            log.setResponse(truncate(response, 8000));
            log.setLatencyMs(latencyMs);
            log.setCreatedAt(LocalDateTime.now());
            logMapper.insert(log);
        } catch (Exception ignored) {
            // 留痕失败不阻断业务（演示环境容忍）
        }
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    /** 输出净化：剥离原生控制字符（LLM偶发输出含VT/FF等控制符，会破坏下游JSON序列化） */
    static String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]", "");
    }

    /** 敏感数据脱敏前置：手机号/身份证进模型前打码 */
    public static String mask(String s) {
        if (s == null) return "";
        String out = IDCARD.matcher(s).replaceAll(m -> m.group().substring(0, 4) + "**********" + m.group().substring(16));
        return PHONE.matcher(out).replaceAll(m -> m.group().substring(0, 3) + "****" + m.group().substring(7));
    }
}

package com.pcmvp.agent.controller;

import com.pcmvp.agent.domain.AgentAnalysis;
import com.pcmvp.agent.service.AnalyzeService;
import com.pcmvp.agent.service.ChatService;
import com.pcmvp.agent.service.DecomposeService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 智能体入口：政策拆解 / 归因分析（人工门禁） / 对话助手 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final DecomposeService decomposeService;
    private final AnalyzeService analyzeService;
    private final ChatService chatService;

    public AgentController(DecomposeService decomposeService, AnalyzeService analyzeService,
                           ChatService chatService) {
        this.decomposeService = decomposeService;
        this.analyzeService = analyzeService;
        this.chatService = chatService;
    }

    /** 触发政策拆解流水线（五步，DeepSeek优先/确定性降级） */
    @PostMapping("/decompose/{policyId}")
    public Map<String, Object> decompose(@PathVariable Long policyId) {
        return decomposeService.decompose(policyId);
    }

    /** 拆解结果人工门禁审批（approved=true → 草稿规则批量发布） */
    @PostMapping("/decompose/{policyId}/approve")
    public Map<String, Object> approveDecompose(@PathVariable Long policyId,
                                                @RequestParam boolean approved,
                                                @RequestParam(required = false) String comment) {
        return decomposeService.approve(policyId, approved, comment);
    }

    /** 风险事件AI归因分析（至人工门禁） */
    @PostMapping("/analyze/{riskEventId}")
    public AgentAnalysis analyze(@PathVariable Long riskEventId) {
        return analyzeService.analyze(riskEventId);
    }

    /** 查询风险事件的分析记录（最新在前） */
    @GetMapping("/analyze")
    public java.util.List<AgentAnalysis> listAnalyze(@RequestParam Long riskEventId) {
        return analyzeService.listByRiskEvent(riskEventId);
    }

    /** 归因审批：approve自动创建工单；reject退回 */
    @PostMapping("/analyze/{analysisId}/approve")
    public Map<String, Object> approveAnalyze(@PathVariable Long analysisId,
                                              @RequestParam boolean approved,
                                              @RequestParam(required = false, defaultValue = "合规管理员") String operator) {
        return analyzeService.approve(analysisId, approved, operator);
    }

    /** 对话式合规助手（条款级问答，答案溯源条号） */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> body) {
        return chatService.chat(body.getOrDefault("question", ""));
    }
}

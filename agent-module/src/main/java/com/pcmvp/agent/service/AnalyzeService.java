package com.pcmvp.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcmvp.agent.domain.AgentAnalysis;
import com.pcmvp.agent.mapper.AgentAnalysisMapper;
import com.pcmvp.audit.domain.Declaration;
import com.pcmvp.audit.domain.RiskEvent;
import com.pcmvp.audit.mapper.DeclarationMapper;
import com.pcmvp.audit.mapper.RiskEventMapper;
import com.pcmvp.audit.service.HashChainService;
import com.pcmvp.llm.DeepSeekClient;
import com.pcmvp.remediation.domain.Ticket;
import com.pcmvp.remediation.mapper.TicketMapper;
import com.pcmvp.rule.domain.Rule;
import com.pcmvp.rule.mapper.RuleMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI归因分析（M3）：风险事件 → LLM归因+整改建议 → 人工门禁 → 审批通过自动创建整改工单。
 * LLM不可用时降级为确定性模板（规则依据+标准建议），链路不断。
 */
@Service
public class AnalyzeService {

    private final AgentAnalysisMapper analysisMapper;
    private final RiskEventMapper riskMapper;
    private final RuleMapper ruleMapper;
    private final DeclarationMapper declMapper;
    private final TicketMapper ticketMapper;
    private final DeepSeekClient llm;
    private final HashChainService chain;

    public AnalyzeService(AgentAnalysisMapper analysisMapper, RiskEventMapper riskMapper,
                          RuleMapper ruleMapper, DeclarationMapper declMapper,
                          TicketMapper ticketMapper, DeepSeekClient llm, HashChainService chain) {
        this.analysisMapper = analysisMapper;
        this.riskMapper = riskMapper;
        this.ruleMapper = ruleMapper;
        this.declMapper = declMapper;
        this.ticketMapper = ticketMapper;
        this.llm = llm;
        this.chain = chain;
    }

    /** 归因分析（至人工门禁）：产出结论与建议，事件置analyzing */
    @Transactional
    public AgentAnalysis analyze(Long riskEventId) {
        RiskEvent e = riskMapper.selectById(riskEventId);
        if (e == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "风险事件不存在");

        Rule rule = ruleMapper.selectOne(new LambdaQueryWrapper<Rule>().eq(Rule::getCode, e.getRuleCode()));
        Declaration d = declMapper.selectOne(new LambdaQueryWrapper<Declaration>()
                .eq(Declaration::getDeclNo, e.getEventRef()));

        String model;
        String result;
        String suggestion;
        String llmOut = null;
        if (llm.isConfigured()) {
            String prompt = "风险事件：" + e.getSummary() + "；命中规则：" + e.getRuleCode()
                    + (rule != null ? "（" + rule.getName() + "，依据：" + rule.getClauseRef() + "）" : "")
                    + "；触发事实：" + e.getDetail() + "\n"
                    + "请给出：1)归因分析（2-3句，指出违规根因与涉及环节）2)整改建议（可执行动作，1-2条）。\n"
                    + "格式：归因：…\n建议：…";
            try {
                llmOut = llm.chat("analyze", "你是国企合规风控专家，语言精炼、面向管理层汇报。", prompt);
            } catch (Exception ignored) {
                llmOut = null;
            }
        }
        if (llmOut != null && !llmOut.startsWith("[LLM_UNAVAILABLE]")) {
            model = "deepseek";
            result = llmOut;
            suggestion = llmOut.contains("建议：") ? llmOut.substring(llmOut.indexOf("建议：") + 3).trim() : "";
        } else {
            model = "demo-fallback";
            result = "归因：该申报命中规则" + e.getRuleCode() + "（"
                    + (rule != null ? rule.getName() + "，依据" + rule.getClauseRef() : e.getRuleName())
                    + "），" + e.getSummary() + "。根因属于"
                    + ("黄".equals(e.getSeverity()) ? "程序要件不完备" : "交易安排违反监管红线")
                    + "，应在申报环节前置拦截。";
            suggestion = defaultSuggestion(e.getRuleCode());
        }

        AgentAnalysis a = new AgentAnalysis();
        a.setRiskEventId(riskEventId);
        a.setResult(result);
        a.setSuggestion(suggestion);
        a.setModel(model);
        a.setStatus("pending");
        analysisMapper.insert(a);

        e.setStatus("analyzing");
        riskMapper.updateById(e);
        chain.append(e.getEventRef(), "AGENT_ANALYZE", "analyze", String.valueOf(riskEventId),
                Map.of("model", model));
        return a;
    }

    /** 人工门禁审批：approve → 自动创建整改工单（附AI建议）、事件置ticketed；reject → 事件回new */
    @Transactional
    public Map<String, Object> approve(Long analysisId, boolean approved, String operator) {
        AgentAnalysis a = analysisMapper.selectById(analysisId);
        if (a == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分析不存在");
        if (!"pending".equals(a.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该分析已审批");
        }
        RiskEvent e = riskMapper.selectById(a.getRiskEventId());
        a.setStatus(approved ? "approved" : "rejected");
        analysisMapper.updateById(a);

        Long ticketId = null;
        if (approved) {
            Ticket t = new Ticket();
            t.setRiskEventId(a.getRiskEventId());
            t.setTitle(e == null ? "整改工单" : e.getSummary() + "整改");
            t.setDescription(a.getResult());
            t.setSuggestion(a.getSuggestion());
            t.setStatus("created");
            t.setHistory("[]");
            ticketMapper.insert(t);
            ticketId = t.getId();
            if (e != null) {
                e.setStatus("ticketed");
                riskMapper.updateById(e);
            }
        } else if (e != null) {
            e.setStatus("new");
            riskMapper.updateById(e);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("analysisId", analysisId);
        out.put("approved", approved);
        out.put("ticketId", ticketId);
        return out;
    }

    /** 查询风险事件的分析记录（最新在前） */
    public java.util.List<AgentAnalysis> listByRiskEvent(Long riskEventId) {
        return analysisMapper.selectList(new LambdaQueryWrapper<AgentAnalysis>()
                .eq(AgentAnalysis::getRiskEventId, riskEventId)
                .orderByDesc(AgentAnalysis::getId));
    }

    private String defaultSuggestion(String ruleCode) {
        return switch (ruleCode == null ? "" : ruleCode) {
            case "R1" -> "撤回非公开协议申报，转入进场挂牌转让流程；或补充豁免情形证明并履行批准程序。";
            case "R2" -> "委托重新评估，取得有效期内评估报告后再行申报。";
            case "R3" -> "调整首次挂牌价至不低于经核准或备案的评估结果。";
            case "R4" -> "补齐党委会/董事会决议编号及决策文件后重新提交。";
            case "R5" -> "重新决策并就成交价偏离挂牌价的情况书面说明理由。";
            default -> "对照命中规则依据条款逐项整改，并在系统内提交整改说明。";
        };
    }
}

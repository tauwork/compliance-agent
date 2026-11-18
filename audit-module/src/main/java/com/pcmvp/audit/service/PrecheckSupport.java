package com.pcmvp.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pcmvp.audit.domain.PrecheckOpinion;
import com.pcmvp.audit.domain.RiskEvent;
import com.pcmvp.audit.mapper.PrecheckOpinionMapper;
import com.pcmvp.audit.mapper.RiskEventMapper;
import com.pcmvp.rule.domain.Rule;
import com.pcmvp.rule.mapper.RuleMapper;
import com.pcmvp.rule.model.RuleHit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 预审共享内核（三场景复用）：意见单构建落库、带病提交风险事件生成（幂等）、哈希链留痕。
 */
@Service
public class PrecheckSupport {

    /** 各场景规则的处置建议（演示口径，条款级映射） */
    static final Map<String, String> ADVICE = Map.ofEntries(
            Map.entry("R1", "建议撤回本次申报，转入进场挂牌转让流程；或补充豁免情形证明材料并履行批准程序。"),
            Map.entry("R2", "评估结果已超1年有效期，建议委托重新评估后再行申报。"),
            Map.entry("R3", "首次挂牌价不得低于经核准或备案的评估结果，建议挂牌价不低于评估值。"),
            Map.entry("R4", "请补齐党委会/董事会决议编号及决策文件后再提交。"),
            Map.entry("R5", "成交价显著低于挂牌价，建议重新决策并书面说明理由。"),
            Map.entry("G1", "担保余额已超净资产50%红线，建议压降存量担保或暂缓新增对外担保。"),
            Map.entry("G2", "被担保方资质不符合要求，建议终止本次担保或待其评级提升、落实足额反担保后再议。"),
            Map.entry("G3", "请补齐党委会/董事会决议编号及决策文件后再提交。"),
            Map.entry("G4", "非控股子企业担保须落实足额反担保措施，建议补充反担保协议后再提交。"),
            Map.entry("L1", "资源性资产租赁应通过公开竞价确定承租方，协议定价仅限批准的例外情形，建议转入公开竞价流程。"),
            Map.entry("L2", "单次租期超过5年上限，建议调整为不超过60个月，或重新评估后按规定报批。"),
            Map.entry("L3", "租金低于市场参考租金，建议按市场参考租金定价或委托重新评估。"),
            Map.entry("L4", "到期续签应重新履行评估、竞价与决策程序，不得直接续签，请退回走全流程。"));

    private final PrecheckOpinionMapper opinionMapper;
    private final RiskEventMapper riskMapper;
    private final RuleMapper ruleMapper;
    private final HashChainService chain;
    private final ObjectMapper om = new ObjectMapper();

    public PrecheckSupport(PrecheckOpinionMapper opinionMapper, RiskEventMapper riskMapper,
                           RuleMapper ruleMapper, HashChainService chain) {
        this.opinionMapper = opinionMapper;
        this.riskMapper = riskMapper;
        this.ruleMapper = ruleMapper;
        this.chain = chain;
    }

    /** 生成并落库预审意见单。scenario 与 declarationId 一起构成意见单归属。 */
    @Transactional
    public PrecheckOpinion buildOpinion(Long declarationId, String scenario, java.util.List<RuleHit> hits) {
        boolean red = hits.stream().anyMatch(h -> "红".equals(h.getSeverity()));
        boolean yellow = hits.stream().anyMatch(h -> "黄".equals(h.getSeverity()));
        PrecheckOpinion op = new PrecheckOpinion();
        op.setDeclarationId(declarationId);
        op.setScenario(scenario);
        op.setResult(red ? "RED" : (yellow ? "YELLOW" : "PASS"));
        op.setHits(renderHits(hits));
        op.setCreatedAt(LocalDateTime.now());
        opinionMapper.insert(op);
        return op;
    }

    /** 带病提交→风险事件（幂等）+哈希链留痕 */
    @Transactional
    public void createRiskEvents(String eventRef, String summary, Map<String, Object> fact, java.util.List<RuleHit> hits) {
        for (RuleHit h : hits) {
            RiskEvent e = new RiskEvent();
            e.setEventRef(eventRef);
            e.setRuleCode(h.getCode());
            e.setRuleName(h.getName());
            e.setSeverity(h.getSeverity());
            e.setSummary(summary + "：" + h.getName());
            try {
                e.setDetail(om.writeValueAsString(fact));
            } catch (Exception ex) {
                e.setDetail("{}");
            }
            e.setStatus("new");
            e.setCreatedAt(LocalDateTime.now());
            Long exists = riskMapper.selectCount(new LambdaQueryWrapper<RiskEvent>()
                    .eq(RiskEvent::getEventRef, eventRef)
                    .eq(RiskEvent::getRuleCode, h.getCode()));
            if (exists == 0) {
                riskMapper.insert(e);
                chain.append(eventRef, "RISK_EVENT", "create",
                        e.getId() == null ? eventRef : String.valueOf(e.getId()),
                        Map.of("rule", h.getCode(), "severity", h.getSeverity()));
            }
        }
    }

    /** 申报与预审两步哈希链留痕 */
    public void appendChain(String eventRef, Object declarationSnapshot, PrecheckOpinion opinion, java.util.List<RuleHit> hits) {
        chain.append(eventRef, "DECL_SUBMIT", "submit", eventRef, declarationSnapshot);
        chain.append(eventRef, "DECL_PRECHECK", "precheck", eventRef,
                Map.of("result", opinion.getResult(), "hits", hits));
    }

    /** 查询某场景下某申报单的最新意见单 */
    public PrecheckOpinion opinionLatest(Long declarationId, String scenario) {
        return opinionMapper.selectOne(new LambdaQueryWrapper<PrecheckOpinion>()
                .eq(PrecheckOpinion::getDeclarationId, declarationId)
                .eq(PrecheckOpinion::getScenario, scenario)
                .orderByDesc(PrecheckOpinion::getId)
                .last("LIMIT 1"));
    }

    private String renderHits(java.util.List<RuleHit> hits) {
        try {
            ArrayNode arr = om.createArrayNode();
            Map<String, Rule> ruleIdx = new HashMap<>();
            for (Rule r : ruleMapper.selectList(null)) {
                ruleIdx.put(r.getCode(), r);
            }
            for (RuleHit h : hits) {
                ObjectNode n = arr.addObject();
                n.put("code", h.getCode());
                n.put("name", h.getName());
                n.put("severity", h.getSeverity());
                Rule r = ruleIdx.get(h.getCode());
                n.put("clauseRef", r == null ? "" : r.getClauseRef());
                n.put("advice", ADVICE.getOrDefault(h.getCode(), "请核对相关政策条款。"));
            }
            return om.writeValueAsString(arr);
        } catch (Exception e) {
            return "[]";
        }
    }
}

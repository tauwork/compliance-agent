package com.pcmvp.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pcmvp.audit.domain.Declaration;
import com.pcmvp.audit.domain.PrecheckOpinion;
import com.pcmvp.audit.domain.RiskEvent;
import com.pcmvp.audit.mapper.DeclarationMapper;
import com.pcmvp.audit.mapper.PrecheckOpinionMapper;
import com.pcmvp.audit.mapper.RiskEventMapper;
import com.pcmvp.rule.domain.Rule;
import com.pcmvp.rule.mapper.RuleMapper;
import com.pcmvp.rule.model.RuleHit;
import com.pcmvp.rule.service.DroolsEvalService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 前置预审服务（链路B核心）：
 * 申报单提交 → 归一化为事实Map（计算 days_since_assessment / deal_discount 等派生字段）
 * → Drools同步评估已发布规则 → 生成预审意见单（红/黄/通过，附条款依据与建议）
 * → 红色意见不阻断提交（MVP无拦截），但"带病提交"生成风险事件
 * → 申报单快照+意见单全部入哈希链留痕（可回放）。
 */
@Service
public class PrecheckService {

    private static final Map<String, String> ADVICE = Map.of(
            "R1", "建议撤回本次申报，转入进场挂牌转让流程；或补充豁免情形证明材料并履行批准程序。",
            "R2", "评估结果已超1年有效期，建议委托重新评估后再行申报。",
            "R3", "首次挂牌价不得低于经核准或备案的评估结果，建议挂牌价不低于评估值。",
            "R4", "请补齐党委会/董事会决议编号及决策文件后再提交。",
            "R5", "成交价显著低于挂牌价，建议重新决策并书面说明理由。");

    private final DeclarationMapper declMapper;
    private final PrecheckOpinionMapper opinionMapper;
    private final RiskEventMapper riskMapper;
    private final RuleMapper ruleMapper;
    private final DroolsEvalService evalService;
    private final HashChainService chain;
    private final ObjectMapper om = new ObjectMapper();

    public PrecheckService(DeclarationMapper declMapper, PrecheckOpinionMapper opinionMapper,
                           RiskEventMapper riskMapper, RuleMapper ruleMapper,
                           DroolsEvalService evalService, HashChainService chain) {
        this.declMapper = declMapper;
        this.opinionMapper = opinionMapper;
        this.riskMapper = riskMapper;
        this.ruleMapper = ruleMapper;
        this.evalService = evalService;
        this.chain = chain;
    }

    /** 提交申报单并同步预审：返回 {declaration, opinion} */
    @Transactional
    public Map<String, Object> submit(Declaration d) {
        // 1. 校验与编号
        if (d.getAssetName() == null || d.getAssetName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资产名称不能为空");
        }
        d.setId(null);
        d.setDeclNo("DECL-" + System.currentTimeMillis());
        if (d.getExemption() == null) d.setExemption(false);
        if (d.getSubmitter() == null || d.getSubmitter().isBlank()) d.setSubmitter("申报人A");
        d.setStatus("submitted");
        d.setCreatedAt(LocalDateTime.now());
        declMapper.insert(d);

        // 2. 归一化事实（含派生字段，供规则DSL消费）
        Map<String, Object> fact = normalize(d);

        // 3. 同步规则评估（确定性判定，场景隔离：仅资产处置规则）
        List<RuleHit> hits = evalService.eval(List.of(fact), "asset");

        // 4. 生成预审意见单
        PrecheckOpinion opinion = buildOpinion(d, hits);
        opinionMapper.insert(opinion);

        // 5. 哈希链留痕：申报快照 + 预审意见
        chain.append(d.getDeclNo(), "DECL_SUBMIT", "submit", d.getDeclNo(), d);
        chain.append(d.getDeclNo(), "DECL_PRECHECK", "precheck", d.getDeclNo(),
                Map.of("result", opinion.getResult(), "hits", hits));

        // 6. 带病提交 → 风险事件（幂等：event_ref+rule_code唯一）
        for (RuleHit h : hits) {
            createRiskEvent(d, h);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("declaration", d);
        out.put("opinion", opinion);
        out.put("hits", hits);
        return out;
    }

    /** 申报单 → 事实Map：原始字段 + 派生字段（null归一化为空串等） */
    Map<String, Object> normalize(Declaration d) {
        Map<String, Object> f = new HashMap<>();
        f.put("disposal_mode", d.getDisposalMode() == null ? "" : d.getDisposalMode());
        f.put("exemption", Boolean.TRUE.equals(d.getExemption()));
        f.put("book_value", d.getBookValue() == null ? null : d.getBookValue().doubleValue());
        f.put("appraised_value", d.getAppraisedValue() == null ? null : d.getAppraisedValue().doubleValue());
        f.put("listing_price", d.getListingPrice() == null ? null : d.getListingPrice().doubleValue());
        f.put("deal_price", d.getDealPrice() == null ? null : d.getDealPrice().doubleValue());
        f.put("resolution_no", d.getResolutionNo() == null ? "" : d.getResolutionNo().trim());
        if (d.getAssessmentDate() != null) {
            f.put("days_since_assessment", ChronoUnit.DAYS.between(d.getAssessmentDate(), LocalDate.now()));
        }
        if (d.getDealPrice() != null && d.getListingPrice() != null
                && d.getListingPrice().doubleValue() > 0) {
            f.put("deal_discount", d.getDealPrice().doubleValue() / d.getListingPrice().doubleValue());
        }
        return f;
    }

    private PrecheckOpinion buildOpinion(Declaration d, List<RuleHit> hits) {
        boolean red = hits.stream().anyMatch(h -> "红".equals(h.getSeverity()));
        boolean yellow = hits.stream().anyMatch(h -> "黄".equals(h.getSeverity()));
        PrecheckOpinion op = new PrecheckOpinion();
        op.setDeclarationId(d.getId());
        op.setScenario("asset");
        op.setResult(red ? "RED" : (yellow ? "YELLOW" : "PASS"));
        op.setHits(renderHits(hits));
        op.setCreatedAt(LocalDateTime.now());
        return op;
    }

    private String renderHits(List<RuleHit> hits) {
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

    private void createRiskEvent(Declaration d, RuleHit h) {
        RiskEvent e = new RiskEvent();
        e.setEventRef(d.getDeclNo());
        e.setRuleCode(h.getCode());
        e.setRuleName(h.getName());
        e.setSeverity(h.getSeverity());
        e.setSummary(d.getAssetName() + "：" + h.getName());
        try {
            e.setDetail(om.writeValueAsString(normalize(d)));
        } catch (Exception ex) {
            e.setDetail("{}");
        }
        e.setStatus("new");
        e.setCreatedAt(LocalDateTime.now());
        // 幂等：同(申报单,规则)不重复建
        Long exists = riskMapper.selectCount(new LambdaQueryWrapper<RiskEvent>()
                .eq(RiskEvent::getEventRef, e.getEventRef())
                .eq(RiskEvent::getRuleCode, e.getRuleCode()));
        if (exists == 0) {
            riskMapper.insert(e);
            chain.append(d.getDeclNo(), "RISK_EVENT", "create", e.getId() == null ? d.getDeclNo() : String.valueOf(e.getId()),
                    Map.of("rule", h.getCode(), "severity", h.getSeverity()));
        }
    }

    public PrecheckOpinion opinionOf(Long declarationId) {
        PrecheckOpinion op = opinionMapper.selectOne(new LambdaQueryWrapper<PrecheckOpinion>()
                .eq(PrecheckOpinion::getDeclarationId, declarationId)
                .eq(PrecheckOpinion::getScenario, "asset")
                .orderByDesc(PrecheckOpinion::getId)
                .last("LIMIT 1"));
        if (op == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预审意见单不存在");
        return op;
    }

    public List<Declaration> declarations() {
        return declMapper.selectList(new LambdaQueryWrapper<Declaration>().orderByDesc(Declaration::getId));
    }

    public Declaration declaration(Long id) {
        Declaration d = declMapper.selectById(id);
        if (d == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "申报单不存在");
        return d;
    }
}

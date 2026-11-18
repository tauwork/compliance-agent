package com.pcmvp.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcmvp.audit.domain.GuaranteeDeclaration;
import com.pcmvp.audit.domain.PrecheckOpinion;
import com.pcmvp.audit.mapper.GuaranteeDeclarationMapper;
import com.pcmvp.rule.model.RuleHit;
import com.pcmvp.rule.service.DroolsEvalService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景A前置预审（资金条线·对外担保）：
 * 归一化事实（余额比例/评级/反担保/决议）→ 规则同步评估（G1-G4）→ 意见单 → 风险事件 → 哈希链留痕。
 */
@Service
public class GuaranteePrecheckService {

    private final GuaranteeDeclarationMapper declMapper;
    private final DroolsEvalService evalService;
    private final PrecheckSupport support;

    public GuaranteePrecheckService(GuaranteeDeclarationMapper declMapper,
                                    DroolsEvalService evalService, PrecheckSupport support) {
        this.declMapper = declMapper;
        this.evalService = evalService;
        this.support = support;
    }

    @Transactional
    public Map<String, Object> submit(GuaranteeDeclaration d) {
        if (d.getGuaranteeee() == null || d.getGuaranteeee().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "被担保方不能为空");
        }
        d.setId(null);
        d.setDeclNo("GUA-" + System.currentTimeMillis());
        if (d.getIsHoldingSub() == null) d.setIsHoldingSub(false);
        if (d.getCounterGuarantee() == null) d.setCounterGuarantee(false);
        if (d.getSubmitter() == null || d.getSubmitter().isBlank()) d.setSubmitter("申报人A");
        if (d.getGuarantor() == null || d.getGuarantor().isBlank()) d.setGuarantor("本企业（演示）");
        d.setStatus("submitted");
        d.setCreatedAt(LocalDateTime.now());
        declMapper.insert(d);

        Map<String, Object> fact = normalize(d);
        List<RuleHit> hits = evalService.eval(List.of(fact), "guarantee");
        PrecheckOpinion opinion = support.buildOpinion(d.getId(), "guarantee", hits);
        support.appendChain(d.getDeclNo(), d, opinion, hits);
        support.createRiskEvents(d.getDeclNo(), "对外担保：" + d.getGuaranteeee(), fact, hits);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("declaration", d);
        out.put("opinion", opinion);
        out.put("hits", hits);
        return out;
    }

    Map<String, Object> normalize(GuaranteeDeclaration d) {
        Map<String, Object> f = new HashMap<>();
        f.put("balance_ratio", d.getBalanceRatio() == null ? null : d.getBalanceRatio().doubleValue());
        f.put("guarantee_amount", d.getGuaranteeAmount() == null ? null : d.getGuaranteeAmount().doubleValue());
        f.put("guaranteeee_rating", d.getGuaranteeeeRating() == null ? "未评级" : d.getGuaranteeeeRating());
        f.put("is_holding_sub", Boolean.TRUE.equals(d.getIsHoldingSub()));
        f.put("counter_guarantee", Boolean.TRUE.equals(d.getCounterGuarantee()));
        f.put("resolution_no", d.getResolutionNo() == null ? "" : d.getResolutionNo().trim());
        return f;
    }

    public PrecheckOpinion opinionOf(Long declarationId) {
        PrecheckOpinion op = support.opinionLatest(declarationId, "guarantee");
        if (op == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预审意见单不存在");
        return op;
    }

    public List<GuaranteeDeclaration> declarations() {
        return declMapper.selectList(new LambdaQueryWrapper<GuaranteeDeclaration>()
                .orderByDesc(GuaranteeDeclaration::getId));
    }

    public GuaranteeDeclaration declaration(Long id) {
        GuaranteeDeclaration d = declMapper.selectById(id);
        if (d == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "担保申报单不存在");
        return d;
    }
}

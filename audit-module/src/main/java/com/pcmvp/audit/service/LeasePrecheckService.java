package com.pcmvp.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcmvp.audit.domain.LeaseDeclaration;
import com.pcmvp.audit.domain.PrecheckOpinion;
import com.pcmvp.audit.mapper.LeaseDeclarationMapper;
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
 * 场景C前置预审（资源条线·资产租赁）：
 * 归一化事实（定价方式/租期/租金对照/续签/决议）→ 规则同步评估（L1-L4）→ 意见单 → 风险事件 → 哈希链留痕。
 */
@Service
public class LeasePrecheckService {

    private final LeaseDeclarationMapper declMapper;
    private final DroolsEvalService evalService;
    private final PrecheckSupport support;

    public LeasePrecheckService(LeaseDeclarationMapper declMapper,
                                DroolsEvalService evalService, PrecheckSupport support) {
        this.declMapper = declMapper;
        this.evalService = evalService;
        this.support = support;
    }

    @Transactional
    public Map<String, Object> submit(LeaseDeclaration d) {
        if (d.getAssetName() == null || d.getAssetName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "租赁资产名称不能为空");
        }
        d.setId(null);
        d.setDeclNo("LEASE-" + System.currentTimeMillis());
        if (d.getIsRenewal() == null) d.setIsRenewal(false);
        if (d.getSubmitter() == null || d.getSubmitter().isBlank()) d.setSubmitter("申报人A");
        d.setStatus("submitted");
        d.setCreatedAt(LocalDateTime.now());
        declMapper.insert(d);

        Map<String, Object> fact = normalize(d);
        List<RuleHit> hits = evalService.eval(List.of(fact), "lease");
        PrecheckOpinion opinion = support.buildOpinion(d.getId(), "lease", hits);
        support.appendChain(d.getDeclNo(), d, opinion, hits);
        support.createRiskEvents(d.getDeclNo(), "资产租赁：" + d.getAssetName(), fact, hits);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("declaration", d);
        out.put("opinion", opinion);
        out.put("hits", hits);
        return out;
    }

    Map<String, Object> normalize(LeaseDeclaration d) {
        Map<String, Object> f = new HashMap<>();
        f.put("pricing_mode", d.getPricingMode() == null ? "" : d.getPricingMode());
        f.put("lease_term_months", d.getLeaseTermMonths() == null ? null : d.getLeaseTermMonths().doubleValue());
        f.put("monthly_rent", d.getMonthlyRent() == null ? null : d.getMonthlyRent().doubleValue());
        f.put("market_rent_ref", d.getMarketRentRef() == null ? null : d.getMarketRentRef().doubleValue());
        f.put("is_renewal", Boolean.TRUE.equals(d.getIsRenewal()));
        f.put("resolution_no", d.getResolutionNo() == null ? "" : d.getResolutionNo().trim());
        return f;
    }

    public PrecheckOpinion opinionOf(Long declarationId) {
        PrecheckOpinion op = support.opinionLatest(declarationId, "lease");
        if (op == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预审意见单不存在");
        return op;
    }

    public List<LeaseDeclaration> declarations() {
        return declMapper.selectList(new LambdaQueryWrapper<LeaseDeclaration>()
                .orderByDesc(LeaseDeclaration::getId));
    }

    public LeaseDeclaration declaration(Long id) {
        LeaseDeclaration d = declMapper.selectById(id);
        if (d == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "租赁申报单不存在");
        return d;
    }
}

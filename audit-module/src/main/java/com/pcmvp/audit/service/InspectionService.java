package com.pcmvp.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcmvp.audit.domain.Declaration;
import com.pcmvp.audit.domain.GuaranteeDeclaration;
import com.pcmvp.audit.domain.InspectionFinding;
import com.pcmvp.audit.domain.InspectionRun;
import com.pcmvp.audit.domain.LeaseDeclaration;
import com.pcmvp.audit.mapper.InspectionFindingMapper;
import com.pcmvp.audit.mapper.InspectionRunMapper;
import com.pcmvp.rule.model.RuleHit;
import com.pcmvp.rule.service.DroolsEvalService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全量巡检：把已入库申报按当前生效规则再扫一遍。
 * 判定与前置预审共用同一解释器；已存在的风险事件不重复写入。
 */
@Service
public class InspectionService {

    private static final Set<String> SCOPES = Set.of("all", "asset", "guarantee", "lease");

    private final PrecheckService assetPrecheck;
    private final GuaranteePrecheckService guaranteePrecheck;
    private final LeasePrecheckService leasePrecheck;
    private final DroolsEvalService evalService;
    private final PrecheckSupport support;
    private final HashChainService chain;
    private final InspectionRunMapper runMapper;
    private final InspectionFindingMapper findingMapper;
    private final ObjectMapper om = new ObjectMapper();

    public InspectionService(PrecheckService assetPrecheck, GuaranteePrecheckService guaranteePrecheck,
                             LeasePrecheckService leasePrecheck, DroolsEvalService evalService,
                             PrecheckSupport support, HashChainService chain,
                             InspectionRunMapper runMapper, InspectionFindingMapper findingMapper) {
        this.assetPrecheck = assetPrecheck;
        this.guaranteePrecheck = guaranteePrecheck;
        this.leasePrecheck = leasePrecheck;
        this.evalService = evalService;
        this.support = support;
        this.chain = chain;
        this.runMapper = runMapper;
        this.findingMapper = findingMapper;
    }

    @Transactional
    public Map<String, Object> run(String scenario) {
        String scope = scenario == null || scenario.isBlank() ? "all" : scenario;
        if (!SCOPES.contains(scope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "场景仅支持 all/asset/guarantee/lease");
        }
        List<InspectionFinding> rows = new ArrayList<>();
        int newRisks = 0;
        if ("all".equals(scope) || "asset".equals(scope)) {
            newRisks += scanAssets(rows);
        }
        if ("all".equals(scope) || "guarantee".equals(scope)) {
            newRisks += scanGuarantees(rows);
        }
        if ("all".equals(scope) || "lease".equals(scope)) {
            newRisks += scanLeases(rows);
        }

        int red = 0, yellow = 0, pass = 0;
        for (InspectionFinding row : rows) {
            if ("RED".equals(row.getResult())) red++;
            else if ("YELLOW".equals(row.getResult())) yellow++;
            else pass++;
        }
        InspectionRun run = new InspectionRun();
        run.setScenario(scope);
        run.setScanned(rows.size());
        run.setHitDecls(red + yellow);
        run.setRedCount(red);
        run.setYellowCount(yellow);
        run.setPassCount(pass);
        run.setNewRisks(newRisks);
        run.setCreatedAt(LocalDateTime.now());
        runMapper.insert(run);
        for (InspectionFinding row : rows) {
            row.setRunId(run.getId());
            findingMapper.insert(row);
        }
        chain.append("INSP-" + run.getId(), "INSPECTION", "scan", String.valueOf(run.getId()),
                Map.of("scenario", scope, "scanned", rows.size(), "hitDecls", red + yellow, "newRisks", newRisks));
        return detail(run.getId());
    }

    public List<InspectionRun> list() {
        return runMapper.selectList(new LambdaQueryWrapper<InspectionRun>().orderByDesc(InspectionRun::getId));
    }

    public Map<String, Object> detail(Long id) {
        InspectionRun run = runMapper.selectById(id);
        if (run == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "巡检批次不存在");
        List<InspectionFinding> rows = findingMapper.selectList(new LambdaQueryWrapper<InspectionFinding>()
                .eq(InspectionFinding::getRunId, id)
                .orderByAsc(InspectionFinding::getId));
        List<Map<String, Object>> findings = new ArrayList<>();
        for (InspectionFinding row : rows) {
            findings.add(toView(row));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("run", run);
        out.put("findings", findings);
        return out;
    }

    private int scanAssets(List<InspectionFinding> rows) {
        int created = 0;
        for (Declaration d : assetPrecheck.declarations()) {
            Map<String, Object> fact = assetPrecheck.normalize(d);
            List<RuleHit> hits = evalService.eval(List.of(fact), "asset");
            created += support.createRiskEvents(d.getDeclNo(), d.getAssetName(), fact, hits);
            rows.add(finding("asset", d.getDeclNo(), d.getAssetName(), hits, assetForm(d)));
        }
        return created;
    }

    private int scanGuarantees(List<InspectionFinding> rows) {
        int created = 0;
        for (GuaranteeDeclaration d : guaranteePrecheck.declarations()) {
            Map<String, Object> fact = guaranteePrecheck.normalize(d);
            List<RuleHit> hits = evalService.eval(List.of(fact), "guarantee");
            created += support.createRiskEvents(d.getDeclNo(), "对外担保：" + d.getGuaranteeee(), fact, hits);
            rows.add(finding("guarantee", d.getDeclNo(), d.getGuaranteeee(), hits, guaranteeForm(d)));
        }
        return created;
    }

    private int scanLeases(List<InspectionFinding> rows) {
        int created = 0;
        for (LeaseDeclaration d : leasePrecheck.declarations()) {
            Map<String, Object> fact = leasePrecheck.normalize(d);
            List<RuleHit> hits = evalService.eval(List.of(fact), "lease");
            created += support.createRiskEvents(d.getDeclNo(), "资产租赁：" + d.getAssetName(), fact, hits);
            rows.add(finding("lease", d.getDeclNo(), d.getAssetName(), hits, leaseForm(d)));
        }
        return created;
    }

    private InspectionFinding finding(String scenario, String declNo, String subject,
                                      List<RuleHit> hits, Map<String, Object> form) {
        boolean red = hits.stream().anyMatch(h -> "红".equals(h.getSeverity()));
        boolean yellow = hits.stream().anyMatch(h -> "黄".equals(h.getSeverity()));
        InspectionFinding row = new InspectionFinding();
        row.setScenario(scenario);
        row.setDeclNo(declNo);
        row.setSubject(subject);
        row.setResult(red ? "RED" : (yellow ? "YELLOW" : "PASS"));
        row.setHits(support.renderHitsJson(hits));
        try {
            row.setFormSnapshot(om.writeValueAsString(form));
        } catch (Exception e) {
            row.setFormSnapshot("{}");
        }
        return row;
    }

    private Map<String, Object> toView(InspectionFinding row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("scenario", row.getScenario());
        view.put("declNo", row.getDeclNo());
        view.put("subject", row.getSubject());
        view.put("result", row.getResult());
        view.put("hits", read(row.getHits(), new TypeReference<List<Map<String, Object>>>() {}));
        view.put("form", read(row.getFormSnapshot(), new TypeReference<Map<String, Object>>() {}));
        return view;
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return om.readValue(json == null ? "null" : json, type);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> assetForm(Declaration d) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("assetName", d.getAssetName());
        f.put("assetCategory", d.getAssetCategory());
        f.put("bookValue", num(d.getBookValue()));
        f.put("appraisedValue", num(d.getAppraisedValue()));
        f.put("assessmentDate", d.getAssessmentDate() == null ? null : d.getAssessmentDate().toString());
        f.put("disposalMode", d.getDisposalMode());
        f.put("exemption", Boolean.TRUE.equals(d.getExemption()));
        f.put("resolutionNo", d.getResolutionNo());
        f.put("listingPrice", num(d.getListingPrice()));
        f.put("dealPrice", num(d.getDealPrice()));
        return f;
    }

    private Map<String, Object> guaranteeForm(GuaranteeDeclaration d) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("guaranteeee", d.getGuaranteeee());
        f.put("guaranteeType", d.getGuaranteeType());
        f.put("guaranteeAmount", num(d.getGuaranteeAmount()));
        f.put("balanceRatio", num(d.getBalanceRatio()));
        f.put("guaranteeeeRating", d.getGuaranteeeeRating());
        f.put("isHoldingSub", Boolean.TRUE.equals(d.getIsHoldingSub()));
        f.put("counterGuarantee", Boolean.TRUE.equals(d.getCounterGuarantee()));
        f.put("resolutionNo", d.getResolutionNo());
        return f;
    }

    private Map<String, Object> leaseForm(LeaseDeclaration d) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("assetName", d.getAssetName());
        f.put("assetType", d.getAssetType());
        f.put("pricingMode", d.getPricingMode());
        f.put("leaseTermMonths", d.getLeaseTermMonths());
        f.put("monthlyRent", num(d.getMonthlyRent()));
        f.put("marketRentRef", num(d.getMarketRentRef()));
        f.put("isRenewal", Boolean.TRUE.equals(d.getIsRenewal()));
        f.put("resolutionNo", d.getResolutionNo());
        return f;
    }

    private static Double num(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}

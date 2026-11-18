package com.pcmvp.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcmvp.audit.domain.Declaration;
import com.pcmvp.audit.domain.GuaranteeDeclaration;
import com.pcmvp.audit.domain.LeaseDeclaration;
import com.pcmvp.audit.domain.PrecheckOpinion;
import com.pcmvp.audit.domain.RiskEvent;
import com.pcmvp.audit.mapper.DeclarationMapper;
import com.pcmvp.audit.mapper.GuaranteeDeclarationMapper;
import com.pcmvp.audit.mapper.LeaseDeclarationMapper;
import com.pcmvp.audit.mapper.PrecheckOpinionMapper;
import com.pcmvp.audit.mapper.RiskEventMapper;
import com.pcmvp.policy.mapper.PolicyMapper;
import com.pcmvp.rule.domain.Rule;
import com.pcmvp.rule.mapper.RuleMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 驾驶舱统计：三资三场景分口径总览，意见单按场景归集。 */
@Service
public class StatsService {

    private static final List<String> SCENARIOS = List.of("asset", "guarantee", "lease");

    private final DeclarationMapper declMapper;
    private final GuaranteeDeclarationMapper guaranteeMapper;
    private final LeaseDeclarationMapper leaseMapper;
    private final PrecheckOpinionMapper opinionMapper;
    private final RiskEventMapper riskMapper;
    private final RuleMapper ruleMapper;
    private final PolicyMapper policyMapper;

    public StatsService(DeclarationMapper declMapper, GuaranteeDeclarationMapper guaranteeMapper,
                        LeaseDeclarationMapper leaseMapper, PrecheckOpinionMapper opinionMapper,
                        RiskEventMapper riskMapper, RuleMapper ruleMapper, PolicyMapper policyMapper) {
        this.declMapper = declMapper;
        this.guaranteeMapper = guaranteeMapper;
        this.leaseMapper = leaseMapper;
        this.opinionMapper = opinionMapper;
        this.riskMapper = riskMapper;
        this.ruleMapper = ruleMapper;
        this.policyMapper = policyMapper;
    }

    public Map<String, Object> overview() {
        long assetDecls = n(declMapper.selectCount(null));
        long guaranteeDecls = n(guaranteeMapper.selectCount(null));
        long leaseDecls = n(leaseMapper.selectCount(null));
        long redOpinions = countOpinion(null, "RED");
        long yellowOpinions = countOpinion(null, "YELLOW");
        long pendingRisks = n(riskMapper.selectCount(new LambdaQueryWrapper<RiskEvent>()
                .in(RiskEvent::getStatus, "new", "analyzing")));
        long totalRisks = n(riskMapper.selectCount(null));
        long closedRisks = n(riskMapper.selectCount(new LambdaQueryWrapper<RiskEvent>()
                .eq(RiskEvent::getStatus, "closed")));

        Map<String, Object> scenarios = new LinkedHashMap<>();
        scenarios.put("asset", bucket("asset", assetDecls));
        scenarios.put("guarantee", bucket("guarantee", guaranteeDecls));
        scenarios.put("lease", bucket("lease", leaseDecls));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policies", n(policyMapper.selectCount(null)));
        out.put("declarations", assetDecls + guaranteeDecls + leaseDecls);
        out.put("publishedRules", n(ruleMapper.selectCount(published(null))));
        out.put("redPrechecks", redOpinions);
        out.put("yellowPrechecks", yellowOpinions);
        out.put("pendingRisks", pendingRisks);
        out.put("totalRisks", totalRisks);
        out.put("closedRisks", closedRisks);
        out.put("closedRate", totalRisks == 0 ? 0 : Math.round(closedRisks * 1000.0 / totalRisks) / 10.0);
        out.put("scenarios", scenarios);
        return out;
    }

    /** 三场景申报最近记录，附该场景下的真实预审结论（不从风险事件反推）。 */
    public List<Map<String, Object>> feed() {
        Map<String, String> opinions = latestOpinions();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Declaration d : declMapper.selectList(new LambdaQueryWrapper<Declaration>()
                .orderByDesc(Declaration::getId).last("LIMIT 20"))) {
            rows.add(row(d.getDeclNo(), d.getAssetName(), "asset", d.getId(), d.getCreatedAt(), opinions));
        }
        for (GuaranteeDeclaration d : guaranteeMapper.selectList(new LambdaQueryWrapper<GuaranteeDeclaration>()
                .orderByDesc(GuaranteeDeclaration::getId).last("LIMIT 20"))) {
            rows.add(row(d.getDeclNo(), "担保：" + d.getGuaranteeee(), "guarantee", d.getId(), d.getCreatedAt(), opinions));
        }
        for (LeaseDeclaration d : leaseMapper.selectList(new LambdaQueryWrapper<LeaseDeclaration>()
                .orderByDesc(LeaseDeclaration::getId).last("LIMIT 20"))) {
            rows.add(row(d.getDeclNo(), "租赁：" + d.getAssetName(), "lease", d.getId(), d.getCreatedAt(), opinions));
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (LocalDateTime) r.get("createdAt"),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return rows.size() > 20 ? rows.subList(0, 20) : rows;
    }

    private Map<String, Object> bucket(String scenario, long declarations) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("declarations", declarations);
        m.put("red", countOpinion(scenario, "RED"));
        m.put("yellow", countOpinion(scenario, "YELLOW"));
        m.put("pass", countOpinion(scenario, "PASS"));
        m.put("publishedRules", n(ruleMapper.selectCount(published(scenario))));
        return m;
    }

    private long countOpinion(String scenario, String result) {
        LambdaQueryWrapper<PrecheckOpinion> q = new LambdaQueryWrapper<PrecheckOpinion>()
                .eq(PrecheckOpinion::getResult, result);
        if (scenario != null) q.eq(PrecheckOpinion::getScenario, scenario);
        return n(opinionMapper.selectCount(q));
    }

    private LambdaQueryWrapper<Rule> published(String scenario) {
        LambdaQueryWrapper<Rule> q = new LambdaQueryWrapper<Rule>()
                .eq(Rule::getStatus, "published")
                .eq(Rule::getEnabled, true);
        if (scenario != null) q.eq(Rule::getScenario, scenario);
        return q;
    }

    private Map<String, String> latestOpinions() {
        List<PrecheckOpinion> all = opinionMapper.selectList(new LambdaQueryWrapper<PrecheckOpinion>()
                .orderByAsc(PrecheckOpinion::getId));
        Map<String, String> latest = new LinkedHashMap<>();
        for (PrecheckOpinion op : all) {
            String scenario = op.getScenario() == null ? "asset" : op.getScenario();
            if (!SCENARIOS.contains(scenario)) scenario = "asset";
            latest.put(scenario + ":" + op.getDeclarationId(), op.getResult());
        }
        return latest;
    }

    private Map<String, Object> row(String declNo, String name, String scenario, Long id,
                                    LocalDateTime createdAt, Map<String, String> opinions) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("declNo", declNo);
        row.put("name", name);
        row.put("scenario", scenario);
        row.put("result", opinions.getOrDefault(scenario + ":" + id, "PASS"));
        row.put("createdAt", createdAt);
        return row;
    }

    private static long n(Long v) {
        return v == null ? 0 : v;
    }
}

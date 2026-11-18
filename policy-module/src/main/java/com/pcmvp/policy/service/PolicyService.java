package com.pcmvp.policy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcmvp.policy.domain.Clause;
import com.pcmvp.policy.domain.Obligation;
import com.pcmvp.policy.domain.Policy;
import com.pcmvp.policy.mapper.ClauseMapper;
import com.pcmvp.policy.mapper.ObligationMapper;
import com.pcmvp.policy.mapper.PolicyMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 政策管理（M1：上传/列表/详情+条款浏览）。
 * M2 接入拆解流水线：上传后触发 agent-module 五步拆解（状态 draft→decomposing→decomposed）。
 */
@Service
public class PolicyService {

    private final PolicyMapper policyMapper;
    private final ClauseMapper clauseMapper;
    private final ObligationMapper obligationMapper;
    private final com.pcmvp.rule.mapper.RuleMapper ruleMapper;

    public PolicyService(PolicyMapper policyMapper, ClauseMapper clauseMapper,
                         ObligationMapper obligationMapper, com.pcmvp.rule.mapper.RuleMapper ruleMapper) {
        this.policyMapper = policyMapper;
        this.clauseMapper = clauseMapper;
        this.obligationMapper = obligationMapper;
        this.ruleMapper = ruleMapper;
    }

    public List<Policy> list() {
        return policyMapper.selectList(new LambdaQueryWrapper<Policy>().orderByDesc(Policy::getId));
    }

    public Policy get(Long id) {
        Policy p = policyMapper.selectById(id);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "政策不存在");
        return p;
    }

    @Transactional
    public Policy create(Policy p) {
        p.setId(null);
        p.setStatus("draft");
        p.setVersion(p.getVersion() == null ? "v1" : p.getVersion());
        policyMapper.insert(p);
        return p;
    }

    /** 人工修正义务（AI产出可改，标记revised_by=human，体现"AI提效、人定性"） */
    @Transactional
    public com.pcmvp.policy.domain.Obligation reviseObligation(Long id, com.pcmvp.policy.domain.Obligation in) {
        com.pcmvp.policy.domain.Obligation o = obligationMapper.selectById(id);
        if (o == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "义务不存在");
        if (in.getDescription() != null) o.setDescription(in.getDescription());
        if (in.getDept() != null) o.setDept(in.getDept());
        if (in.getThreshold() != null) o.setThreshold(in.getThreshold());
        if (in.getDeadline() != null) o.setDeadline(in.getDeadline());
        o.setRevisedBy("human");
        obligationMapper.updateById(o);
        return o;
    }

    /** 政策详情（含条款与义务，五级视图的 M1 简化版：政策→条款→义务） */
    public Map<String, Object> detail(Long id) {
        Policy p = get(id);
        List<Clause> clauses = clauseMapper.selectList(
                new LambdaQueryWrapper<Clause>().eq(Clause::getPolicyId, id).orderByAsc(Clause::getId));
        List<Map<String, Object>> clauseViews = clauses.stream().map(c -> {
            Map<String, Object> cv = new LinkedHashMap<>();
            cv.put("clause", c);
            cv.put("obligations", obligationMapper.selectList(
                    new LambdaQueryWrapper<Obligation>().eq(Obligation::getClauseId, c.getId())));
            return cv;
        }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policy", p);
        out.put("clauses", clauseViews);
        out.put("ruleDrafts", ruleMapper.selectList(
                new LambdaQueryWrapper<com.pcmvp.rule.domain.Rule>()
                        .eq(com.pcmvp.rule.domain.Rule::getClauseId,
                                clauses.isEmpty() ? -1L : clauses.get(0).getId())
                        .or().like(com.pcmvp.rule.domain.Rule::getCode, "D-" + id + "-")
                        .orderByAsc(com.pcmvp.rule.domain.Rule::getCode)));
        return out;
    }
}

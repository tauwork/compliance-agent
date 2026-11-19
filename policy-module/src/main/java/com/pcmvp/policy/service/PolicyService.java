package com.pcmvp.policy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pcmvp.policy.domain.Clause;
import com.pcmvp.policy.domain.Obligation;
import com.pcmvp.policy.domain.Policy;
import com.pcmvp.policy.domain.PolicyCategory;
import com.pcmvp.policy.domain.PolicyLibrary;
import com.pcmvp.policy.mapper.ClauseMapper;
import com.pcmvp.policy.mapper.ObligationMapper;
import com.pcmvp.policy.mapper.PolicyCategoryMapper;
import com.pcmvp.policy.mapper.PolicyLibraryMapper;
import com.pcmvp.policy.mapper.PolicyMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final PolicyCategoryMapper categoryMapper;
    private final PolicyLibraryMapper libraryMapper;
    private final PolicyDocService docService;

    public PolicyService(PolicyMapper policyMapper, ClauseMapper clauseMapper,
                         ObligationMapper obligationMapper, com.pcmvp.rule.mapper.RuleMapper ruleMapper,
                         PolicyCategoryMapper categoryMapper, PolicyLibraryMapper libraryMapper,
                         PolicyDocService docService) {
        this.policyMapper = policyMapper;
        this.clauseMapper = clauseMapper;
        this.obligationMapper = obligationMapper;
        this.ruleMapper = ruleMapper;
        this.categoryMapper = categoryMapper;
        this.libraryMapper = libraryMapper;
        this.docService = docService;
    }

    public List<Map<String, Object>> tree() {
        List<Policy> policies = policyMapper.selectList(null);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PolicyCategory category : categoryMapper.selectList(
                new LambdaQueryWrapper<PolicyCategory>().orderByAsc(PolicyCategory::getId))) {
            List<Map<String, Object>> libs = new ArrayList<>();
            for (PolicyLibrary lib : libraryMapper.selectList(new LambdaQueryWrapper<PolicyLibrary>()
                    .eq(PolicyLibrary::getCategoryId, category.getId())
                    .orderByAsc(PolicyLibrary::getId))) {
                long count = policies.stream().filter(p -> lib.getId().equals(p.getLibraryId())).count();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", lib.getId());
                row.put("name", lib.getName());
                row.put("description", lib.getDescription());
                row.put("policyCount", count);
                libs.add(row);
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", category.getId());
            node.put("name", category.getName());
            node.put("libraries", libs);
            out.add(node);
        }
        return out;
    }

    @Transactional
    public PolicyCategory createCategory(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类名称必填");
        }
        PolicyCategory category = new PolicyCategory();
        category.setName(name.trim());
        category.setCreatedAt(LocalDateTime.now());
        categoryMapper.insert(category);
        return category;
    }

    @Transactional
    public PolicyLibrary createLibrary(Long categoryId, String name, String description) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类不存在");
        }
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "子库名称必填");
        }
        PolicyLibrary lib = new PolicyLibrary();
        lib.setCategoryId(categoryId);
        lib.setName(name.trim());
        lib.setDescription(description);
        lib.setCreatedAt(LocalDateTime.now());
        libraryMapper.insert(lib);
        return lib;
    }

    public List<Policy> list(Long libraryId, String validity) {
        LambdaQueryWrapper<Policy> q = new LambdaQueryWrapper<Policy>().orderByDesc(Policy::getId);
        if (libraryId != null) q.eq(Policy::getLibraryId, libraryId);
        List<Policy> rows = policyMapper.selectList(q);
        rows.forEach(this::markValidity);
        if ("effective".equals(validity) || "expired".equals(validity)) {
            return rows.stream().filter(p -> validity.equals(p.getValidity())).toList();
        }
        return rows;
    }

    private void markValidity(Policy p) {
        boolean expired = p.getExpireDate() != null && p.getExpireDate().isBefore(LocalDate.now());
        p.setValidity(expired ? "expired" : "effective");
    }

    public Policy get(Long id) {
        Policy p = policyMapper.selectById(id);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "政策不存在");
        markValidity(p);
        return p;
    }

    @Transactional
    public Policy create(Policy p) {
        p.setId(null);
        p.setStatus("draft");
        p.setVersion(p.getVersion() == null ? "v1" : p.getVersion());
        if (p.getLibraryId() == null) {
            PolicyLibrary fallback = libraryMapper.selectOne(new LambdaQueryWrapper<PolicyLibrary>()
                    .eq(PolicyLibrary::getName, "未归类").last("LIMIT 1"));
            if (fallback != null) p.setLibraryId(fallback.getId());
        }
        policyMapper.insert(p);
        docService.rebuild(p.getId());
        return get(p.getId());
    }

    @Transactional
    public Policy update(Long id, Policy in) {
        get(id);
        policyMapper.update(null, new LambdaUpdateWrapper<Policy>()
                .eq(Policy::getId, id)
                .set(Policy::getExpireDate, in.getExpireDate())
                .set(in.getLibraryId() != null, Policy::getLibraryId, in.getLibraryId()));
        return get(id);
    }

    /** 本政策拆出的规则，加上种子政策对应的已发布规则。 */
    public List<com.pcmvp.rule.domain.Rule> rulesOf(Long id) {
        Policy p = get(id);
        List<Long> clauseIds = clauseMapper.selectList(new LambdaQueryWrapper<Clause>()
                        .eq(Clause::getPolicyId, id))
                .stream().map(Clause::getId).toList();
        String refKey = null;
        if (p.getTitle() != null && p.getTitle().contains("（演示版）")) {
            if (p.getTitle().contains("担保")) refKey = "担保";
            else if (p.getTitle().contains("租赁")) refKey = "租赁";
            else if (p.getTitle().contains("处置")) refKey = "演示办法";
        }
        LambdaQueryWrapper<com.pcmvp.rule.domain.Rule> q = new LambdaQueryWrapper<>();
        String codePrefix = "D-" + id + "-";
        String key = refKey;
        q.and(w -> {
            w.likeRight(com.pcmvp.rule.domain.Rule::getCode, codePrefix);
            if (!clauseIds.isEmpty()) w.or().in(com.pcmvp.rule.domain.Rule::getClauseId, clauseIds);
            if (key != null) w.or().like(com.pcmvp.rule.domain.Rule::getClauseRef, key);
        });
        q.orderByAsc(com.pcmvp.rule.domain.Rule::getCode);
        return ruleMapper.selectList(q);
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

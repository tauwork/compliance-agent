package com.pcmvp.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcmvp.rule.domain.Rule;
import com.pcmvp.rule.mapper.RuleMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 规则管理：CRUD、启停热更新、草稿状态流转（draft→trial→published/rejected） */
@Service
public class RuleService {

    private final RuleMapper mapper;
    private final DroolsEvalService evalService;

    public RuleService(RuleMapper mapper, DroolsEvalService evalService) {
        this.mapper = mapper;
        this.evalService = evalService;
    }

    public List<Rule> list(String status) {
        LambdaQueryWrapper<Rule> q = new LambdaQueryWrapper<Rule>().orderByAsc(Rule::getCode);
        if (status != null && !status.isBlank()) {
            q.eq(Rule::getStatus, status);
        }
        return mapper.selectList(q);
    }

    public Rule get(Long id) {
        Rule r = mapper.selectById(id);
        if (r == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "规则不存在");
        return r;
    }

    @Transactional
    public Rule create(Rule r) {
        r.setId(null);
        if (r.getStatus() == null) r.setStatus("draft");
        if (r.getEnabled() == null) r.setEnabled(true);
        if (r.getVersion() == null) r.setVersion(1L);
        mapper.insert(r);
        evalService.invalidate();
        return r;
    }

    @Transactional
    public Rule update(Long id, Rule in) {
        Rule r = get(id);
        if (in.getName() != null) r.setName(in.getName());
        if (in.getSeverity() != null) r.setSeverity(in.getSeverity());
        if (in.getDsl() != null) r.setDsl(in.getDsl());
        if (in.getClauseRef() != null) r.setClauseRef(in.getClauseRef());
        r.touch();
        mapper.updateById(r);
        evalService.invalidate();
        return r;
    }

    @Transactional
    public void remove(Long id) {
        mapper.deleteById(get(id).getId());
        evalService.invalidate();
    }

    /** 启停（热更新：下一次评估自动重编译） */
    @Transactional
    public Rule toggle(Long id) {
        Rule r = get(id);
        r.setEnabled(!Boolean.TRUE.equals(r.getEnabled()));
        r.touch();
        mapper.updateById(r);
        evalService.invalidate();
        return r;
    }

    /** 草稿状态流转：trial试跑 / publish发布 / reject驳回（发布须人工审批，M2接入门禁记录） */
    @Transactional
    public Rule transition(Long id, String action) {
        Rule r = get(id);
        String target = switch (action) {
            case "trial" -> "trial";
            case "publish" -> "published";
            case "reject" -> "rejected";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的动作: " + action);
        };
        r.setStatus(target);
        r.touch();
        mapper.updateById(r);
        evalService.invalidate();
        return r;
    }
}

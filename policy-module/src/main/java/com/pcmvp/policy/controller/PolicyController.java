package com.pcmvp.policy.controller;

import com.pcmvp.policy.domain.Policy;
import com.pcmvp.policy.service.PolicyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    @GetMapping
    public List<Policy> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Policy get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 政策详情：政策+条款+义务五级视图（M1简化版，M2补控制点与规则草稿联动） */
    @GetMapping("/{id}/decompose")
    public Map<String, Object> decomposeView(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    public Policy create(@RequestBody Policy p) {
        return service.create(p);
    }

    /** 人工修正义务/控制点（AI产出可改，标记revised_by=human） */
    @PutMapping("/obligations/{id}")
    public com.pcmvp.policy.domain.Obligation reviseObligation(
            @PathVariable Long id,
            @RequestBody com.pcmvp.policy.domain.Obligation in) {
        return service.reviseObligation(id, in);
    }
}

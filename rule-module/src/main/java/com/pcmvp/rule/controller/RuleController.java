package com.pcmvp.rule.controller;

import com.pcmvp.rule.domain.Rule;
import com.pcmvp.rule.service.RuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleService service;

    public RuleController(RuleService service) {
        this.service = service;
    }

    @GetMapping
    public List<Rule> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public Rule get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public Rule create(@RequestBody Rule rule) {
        return service.create(rule);
    }

    @PutMapping("/{id}")
    public Rule update(@PathVariable Long id, @RequestBody Rule rule) {
        return service.update(id, rule);
    }

    @DeleteMapping("/{id}")
    public void remove(@PathVariable Long id) {
        service.remove(id);
    }

    @PutMapping("/{id}/toggle")
    public Rule toggle(@PathVariable Long id) {
        return service.toggle(id);
    }

    /** 草稿状态流转：action = trial / publish / reject */
    @PostMapping("/{id}/transition")
    public Rule transition(@PathVariable Long id, @RequestParam String action) {
        return service.transition(id, action);
    }
}

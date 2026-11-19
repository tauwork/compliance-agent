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
    private final com.pcmvp.policy.service.PolicyDocService docs;

    public PolicyController(PolicyService service, com.pcmvp.policy.service.PolicyDocService docs) {
        this.service = service;
        this.docs = docs;
    }

    @GetMapping("/tree")
    public List<Map<String, Object>> tree() {
        return service.tree();
    }

    @PostMapping("/categories")
    public com.pcmvp.policy.domain.PolicyCategory createCategory(@RequestBody Map<String, String> body) {
        return service.createCategory(body.get("name"));
    }

    @PostMapping("/libraries")
    public com.pcmvp.policy.domain.PolicyLibrary createLibrary(@RequestBody Map<String, Object> body) {
        Object categoryId = body.get("categoryId");
        Long id = categoryId == null ? null : Long.valueOf(categoryId.toString());
        return service.createLibrary(id, (String) body.get("name"), (String) body.get("description"));
    }

    @GetMapping
    public List<Policy> list(@RequestParam(required = false) Long libraryId,
                             @RequestParam(required = false) String validity) {
        return service.list(libraryId, validity);
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

    @PutMapping("/{id}")
    public Policy update(@PathVariable Long id, @RequestBody Policy p) {
        return service.update(id, p);
    }

    @GetMapping("/{id}/chunks")
    public List<com.pcmvp.policy.domain.PolicyChunk> chunks(@PathVariable Long id) {
        return docs.chunks(id);
    }

    @PostMapping("/{id}/chunks/rebuild")
    public List<com.pcmvp.policy.domain.PolicyChunk> rebuild(@PathVariable Long id) {
        return docs.rebuild(id);
    }

    @PutMapping("/{id}/chunks/{chunkId}")
    public com.pcmvp.policy.domain.PolicyChunk saveChunk(@PathVariable Long id, @PathVariable Long chunkId,
                                                         @RequestBody com.pcmvp.policy.domain.PolicyChunk in) {
        return docs.saveChunk(id, chunkId, in.getContent(), in.getEnabled());
    }

    @DeleteMapping("/{id}/chunks/{chunkId}")
    public void deleteChunk(@PathVariable Long id, @PathVariable Long chunkId) {
        docs.deleteChunk(id, chunkId);
    }

    @GetMapping("/{id}/qas")
    public List<com.pcmvp.policy.domain.PolicyQa> qas(@PathVariable Long id) {
        return docs.qas(id);
    }

    @PostMapping("/{id}/qas/generate")
    public List<com.pcmvp.policy.domain.PolicyQa> generateQas(@PathVariable Long id,
                                                             @RequestParam(defaultValue = "false") boolean replace) {
        return docs.generateQas(id, replace);
    }

    @PostMapping("/{id}/qas")
    public com.pcmvp.policy.domain.PolicyQa addQa(@PathVariable Long id, @RequestBody com.pcmvp.policy.domain.PolicyQa in) {
        return docs.addQa(id, in.getQuestion(), in.getAnswer());
    }

    @DeleteMapping("/{id}/qas/{qaId}")
    public void deleteQa(@PathVariable Long id, @PathVariable Long qaId) {
        docs.deleteQa(id, qaId);
    }

    @GetMapping("/{id}/rules")
    public List<com.pcmvp.rule.domain.Rule> rules(@PathVariable Long id) {
        return service.rulesOf(id);
    }

    /** 人工修正义务/控制点（AI产出可改，标记revised_by=human） */
    @PutMapping("/obligations/{id}")
    public com.pcmvp.policy.domain.Obligation reviseObligation(
            @PathVariable Long id,
            @RequestBody com.pcmvp.policy.domain.Obligation in) {
        return service.reviseObligation(id, in);
    }
}

package com.pcmvp.audit.controller;

import com.pcmvp.audit.domain.Declaration;
import com.pcmvp.audit.domain.PrecheckOpinion;
import com.pcmvp.audit.service.HashChainService;
import com.pcmvp.audit.service.PrecheckService;
import com.pcmvp.audit.service.RiskEventService;
import com.pcmvp.audit.service.StatsService;
import com.pcmvp.audit.domain.RiskEvent;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 合规运行入口：前置预审 / 申报单 / 风险事件 / 驾驶舱统计 / 哈希链校验 */
@RestController
public class AuditController {

    private final PrecheckService precheckService;
    private final RiskEventService riskEventService;
    private final StatsService statsService;
    private final HashChainService chainService;
    private final com.pcmvp.audit.service.InspectionService inspectionService;

    public AuditController(PrecheckService precheckService, RiskEventService riskEventService,
                           StatsService statsService, HashChainService chainService,
                           com.pcmvp.audit.service.GuaranteePrecheckService guaranteePrecheckService,
                           com.pcmvp.audit.service.LeasePrecheckService leasePrecheckService,
                           com.pcmvp.audit.service.InspectionService inspectionService) {
        this.precheckService = precheckService;
        this.riskEventService = riskEventService;
        this.statsService = statsService;
        this.chainService = chainService;
        this.guaranteePrecheckService = guaranteePrecheckService;
        this.leasePrecheckService = leasePrecheckService;
        this.inspectionService = inspectionService;
    }

    /** 提交资产处置申报单并同步预审（返回申报单+意见单+命中明细） */
    @PostMapping("/api/precheck")
    public Map<String, Object> submit(@RequestBody Declaration d) {
        return precheckService.submit(d);
    }

    // ============ 场景A：对外担保 ============

    private final com.pcmvp.audit.service.GuaranteePrecheckService guaranteePrecheckService;
    private final com.pcmvp.audit.service.LeasePrecheckService leasePrecheckService;

    @PostMapping("/api/precheck/guarantee")
    public Map<String, Object> submitGuarantee(@RequestBody com.pcmvp.audit.domain.GuaranteeDeclaration d) {
        return guaranteePrecheckService.submit(d);
    }

    @GetMapping("/api/precheck/guarantee/{declarationId}/opinion")
    public PrecheckOpinion guaranteeOpinion(@PathVariable Long declarationId) {
        return guaranteePrecheckService.opinionOf(declarationId);
    }

    @GetMapping("/api/guarantee-declarations")
    public List<com.pcmvp.audit.domain.GuaranteeDeclaration> guaranteeDeclarations() {
        return guaranteePrecheckService.declarations();
    }

    // ============ 场景C：资产租赁 ============

    @PostMapping("/api/precheck/lease")
    public Map<String, Object> submitLease(@RequestBody com.pcmvp.audit.domain.LeaseDeclaration d) {
        return leasePrecheckService.submit(d);
    }

    @GetMapping("/api/precheck/lease/{declarationId}/opinion")
    public PrecheckOpinion leaseOpinion(@PathVariable Long declarationId) {
        return leasePrecheckService.opinionOf(declarationId);
    }

    @GetMapping("/api/lease-declarations")
    public List<com.pcmvp.audit.domain.LeaseDeclaration> leaseDeclarations() {
        return leasePrecheckService.declarations();
    }

    @GetMapping("/api/precheck/{declarationId}/opinion")
    public PrecheckOpinion opinion(@PathVariable Long declarationId) {
        return precheckService.opinionOf(declarationId);
    }

    @GetMapping("/api/declarations")
    public List<Declaration> declarations() {
        return precheckService.declarations();
    }

    @GetMapping("/api/declarations/{id}")
    public Declaration declaration(@PathVariable Long id) {
        return precheckService.declaration(id);
    }

    @GetMapping("/api/risk-events")
    public List<RiskEvent> riskEvents(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) String severity) {
        return riskEventService.list(status, severity);
    }

    @GetMapping("/api/risk-events/{id}")
    public RiskEvent riskEvent(@PathVariable Long id) {
        RiskEvent e = riskEventService.get(id);
        if (e == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "风险事件不存在");
        return e;
    }

    @PutMapping("/api/risk-events/{id}/status")
    public void updateRiskStatus(@PathVariable Long id, @RequestParam String status) {
        riskEventService.updateStatus(id, status);
    }

    @GetMapping("/api/stats/overview")
    public Map<String, Object> overview() {
        return statsService.overview();
    }

    /** 三场景最近申报，附真实预审结论 */
    @GetMapping("/api/precheck/feed")
    public List<Map<String, Object>> precheckFeed() {
        return statsService.feed();
    }

    /** 全量巡检：扫描已入库申报。scenario=all/asset/guarantee/lease */
    @PostMapping("/api/inspections")
    public Map<String, Object> inspect(@RequestParam(defaultValue = "all") String scenario) {
        return inspectionService.run(scenario);
    }

    @GetMapping("/api/inspections")
    public List<com.pcmvp.audit.domain.InspectionRun> inspections() {
        return inspectionService.list();
    }

    @GetMapping("/api/inspections/{id}")
    public Map<String, Object> inspection(@PathVariable Long id) {
        return inspectionService.detail(id);
    }

    /** 哈希链全链校验（演示防篡改能力） */
    @GetMapping("/api/audit/chain/verify")
    public Map<String, Object> verifyChain() {
        return chainService.verifyChain();
    }
}

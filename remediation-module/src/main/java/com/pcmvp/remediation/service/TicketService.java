package com.pcmvp.remediation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pcmvp.audit.service.HashChainService;
import com.pcmvp.audit.service.RiskEventService;
import com.pcmvp.remediation.domain.Ticket;
import com.pcmvp.remediation.mapper.TicketMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工单状态机（移植自既有合规Agent MVP·remediation-service）：
 * created → assigned → executing → resolved → verified → closed
 * 任意状态可 reject → rejected → closed
 * verify 通过时进程内联动风险事件闭环（原REST调用改为单体直接调用）。
 */
@Service
public class TicketService {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "created", Set.of("assigned", "rejected"),
            "assigned", Set.of("executing", "rejected"),
            "executing", Set.of("resolved", "rejected"),
            "resolved", Set.of("verified", "rejected"),
            "verified", Set.of("closed"),
            "rejected", Set.of("closed"));

    private final TicketMapper repo;
    private final RiskEventService riskEventService;
    private final HashChainService chain;
    private final ObjectMapper om = new ObjectMapper();

    public TicketService(TicketMapper repo, RiskEventService riskEventService, HashChainService chain) {
        this.repo = repo;
        this.riskEventService = riskEventService;
        this.chain = chain;
    }

    public List<Ticket> list(String status) {
        LambdaQueryWrapper<Ticket> q = new LambdaQueryWrapper<Ticket>().orderByDesc(Ticket::getId);
        if (status != null && !status.isBlank()) {
            q.eq(Ticket::getStatus, status);
        }
        return repo.selectList(q);
    }

    public Ticket get(Long id) {
        Ticket t = repo.selectById(id);
        if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在");
        return t;
    }

    @Transactional
    public Ticket create(Ticket t) {
        t.setId(null);
        t.setStatus("created");
        t.setHistory("[]");
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        repo.insert(t);
        chain.append("TK-" + t.getId(), "TICKET", "create", String.valueOf(t.getId()),
                Map.of("title", t.getTitle()));
        return t;
    }

    @Transactional
    public Ticket transition(Long id, String action, String operator, String comment) {
        String target = switch (action) {
            case "assign" -> "assigned";
            case "execute" -> "executing";
            case "resolve" -> "resolved";
            case "verify" -> "verified";
            case "reject" -> "rejected";
            case "close" -> "closed";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的动作: " + action);
        };
        Ticket t = get(id);
        Set<String> allowed = TRANSITIONS.get(t.getStatus());
        if (allowed == null || !allowed.contains(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "工单状态 " + t.getStatus() + " 不允许流转到 " + target);
        }
        t.setStatus(target);
        appendHistory(t, action, operator, comment);
        t.setUpdatedAt(LocalDateTime.now());
        repo.updateById(t);
        chain.append("TK-" + t.getId(), "TICKET", action, String.valueOf(id),
                Map.of("from", t.getStatus(), "to", target));

        // 复核通过 → 风险事件闭环（进程内联动）
        if ("verify".equals(action) && t.getRiskEventId() != null) {
            riskEventService.closeByEvent(t.getRiskEventId());
        }
        return t;
    }

    @Transactional
    public Ticket assign(Long id, String assignee) {
        Ticket t = get(id);
        t.setAssignee(assignee);
        t.setUpdatedAt(LocalDateTime.now());
        repo.updateById(t);
        return t;
    }

    public Map<String, Object> stats() {
        long total = repo.selectCount(null);
        long closed = repo.selectCount(new LambdaQueryWrapper<Ticket>()
                .in(Ticket::getStatus, "closed", "verified"));
        long pending = repo.selectCount(new LambdaQueryWrapper<Ticket>()
                .in(Ticket::getStatus, "created", "assigned", "executing"));
        return Map.of(
                "total", total,
                "closed", closed,
                "pending", pending,
                "closedRate", total == 0 ? 0 : Math.round(closed * 1000.0 / total) / 10.0);
    }

    private void appendHistory(Ticket t, String action, String operator, String comment) {
        try {
            ArrayNode arr = (ArrayNode) om.readTree(t.getHistory() == null ? "[]" : t.getHistory());
            ObjectNode node = arr.addObject();
            node.put("action", action);
            node.put("operator", operator == null ? "" : operator);
            node.put("comment", comment == null ? "" : comment);
            node.put("at", LocalDateTime.now().toString());
            t.setHistory(om.writeValueAsString(arr));
        } catch (Exception ignored) {
        }
    }
}

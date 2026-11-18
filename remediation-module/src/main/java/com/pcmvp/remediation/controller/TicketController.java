package com.pcmvp.remediation.controller;

import com.pcmvp.remediation.domain.Ticket;
import com.pcmvp.remediation.service.TicketService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @GetMapping
    public List<Ticket> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public Ticket get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public Ticket create(@RequestBody Ticket t) {
        return service.create(t);
    }

    /** 工单流转：action=assign/execute/resolve/verify/reject/close */
    @PostMapping("/{id}/transition")
    public Ticket transition(@PathVariable Long id, @RequestParam String action,
                             @RequestParam(required = false) String operator,
                             @RequestParam(required = false) String comment) {
        return service.transition(id, action, operator, comment);
    }

    @PostMapping("/{id}/assign")
    public Ticket assign(@PathVariable Long id, @RequestParam String assignee) {
        return service.assign(id, assignee);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return service.stats();
    }
}

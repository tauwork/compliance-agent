package com.pcmvp.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcmvp.audit.domain.RiskEvent;
import com.pcmvp.audit.mapper.RiskEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** 风险事件：查询、状态回写（工单复核通过联动闭环） */
@Service
public class RiskEventService {

    private final RiskEventMapper mapper;
    private final HashChainService chain;

    public RiskEventService(RiskEventMapper mapper, HashChainService chain) {
        this.mapper = mapper;
        this.chain = chain;
    }

    public List<RiskEvent> list(String status, String severity) {
        LambdaQueryWrapper<RiskEvent> q = new LambdaQueryWrapper<RiskEvent>().orderByDesc(RiskEvent::getId);
        if (status != null && !status.isBlank()) q.eq(RiskEvent::getStatus, status);
        if (severity != null && !severity.isBlank()) q.eq(RiskEvent::getSeverity, severity);
        return mapper.selectList(q);
    }

    public RiskEvent get(Long id) {
        return mapper.selectById(id);
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        RiskEvent e = mapper.selectById(id);
        if (e == null) return;
        e.setStatus(status);
        mapper.updateById(e);
        chain.append(e.getEventRef(), "RISK_EVENT", "status:" + status, String.valueOf(id), Map.of("status", status));
    }

    /** 工单复核通过时联动闭环（remediation模块进程内调用） */
    @Transactional
    public void closeByEvent(Long riskEventId) {
        updateStatus(riskEventId, "closed");
    }
}

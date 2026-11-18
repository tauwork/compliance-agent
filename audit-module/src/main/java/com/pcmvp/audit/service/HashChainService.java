package com.pcmvp.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcmvp.audit.domain.AuditLog;
import com.pcmvp.audit.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 日志哈希链服务（MySQL实现）：每行 hash = sha256(prev_hash + 行内容) 串联。
 * 移植自既有合规Agent MVP（ClickHouse方案），算法不变，存储换MySQL。
 * 链头 GENESIS；任何一行的篡改都会导致其后所有行校验失败。
 */
@Service
public class HashChainService {

    public static final String GENESIS = "GENESIS";
    /** 哈希原文时间格式：秒级（与MySQL DATETIME精度一致，保证回读值与哈希原文一致） */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditLogMapper mapper;
    private final ObjectMapper om = new ObjectMapper();

    public HashChainService(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    public String lastHash() {
        AuditLog last = mapper.selectOne(new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getId)
                .last("LIMIT 1"));
        return last == null ? GENESIS : last.getHash();
    }

    /** 追加一行到哈希链（事务内与业务写入原子提交） */
    @Transactional
    public AuditLog append(String eventId, String bizType, String action, String entityRef, Object payload) {
        AuditLog row = new AuditLog();
        row.setEventId(eventId);
        // 截断到秒：与DATETIME列精度一致，防回读哈希原文漂移
        row.setTs(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        row.setBizType(bizType);
        row.setAction(action);
        row.setEntityRef(entityRef);
        try {
            row.setPayload(om.writeValueAsString(payload == null ? Map.of() : payload));
        } catch (Exception e) {
            row.setPayload("{}");
        }
        String prev = lastHash();
        row.setPrevHash(prev);
        row.setHash(computeHash(row));
        mapper.insert(row);
        return row;
    }

    public String computeHash(AuditLog r) {
        String content = String.join("|",
                r.getEventId(),
                r.getTs() == null ? "" : r.getTs().format(FMT),
                r.getBizType(), r.getAction(), r.getEntityRef(),
                r.getPayload() == null ? "" : r.getPayload());
        return sha256(prevOf(r) + "|" + content);
    }

    private String prevOf(AuditLog r) {
        return r.getPrevHash() == null ? GENESIS : r.getPrevHash();
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 全链校验：从链首逐行重算哈希比对，并校验 prev_hash 串联关系。
     * 返回校验摘要 {total, valid, brokenAt}
     */
    public Map<String, Object> verifyChain() {
        List<AuditLog> rows = mapper.selectList(new LambdaQueryWrapper<AuditLog>().orderByAsc(AuditLog::getId));
        String prev = GENESIS;
        int brokenAt = -1;
        for (AuditLog r : rows) {
            boolean linkOk = prev.equals(r.getPrevHash());
            String expect = sha256(prev + "|" + String.join("|",
                    r.getEventId(),
                    r.getTs() == null ? "" : r.getTs().format(FMT),
                    r.getBizType(), r.getAction(), r.getEntityRef(),
                    r.getPayload() == null ? "" : r.getPayload()));
            if (!linkOk || !expect.equals(r.getHash())) {
                brokenAt = r.getId().intValue();
                break;
            }
            prev = r.getHash();
        }
        return Map.of("total", rows.size(), "valid", brokenAt < 0, "brokenAt", brokenAt);
    }
}

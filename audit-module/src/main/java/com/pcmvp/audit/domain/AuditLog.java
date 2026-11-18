package com.pcmvp.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 日志哈希链行：hash = sha256(prev_hash + 行内容) 串联防篡改（MySQL实现，替代ClickHouse方案） */
@Getter
@Setter
@NoArgsConstructor
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private LocalDateTime ts;

    private String bizType;   // DECL_SUBMIT/DECL_PRECHECK/RISK_EVENT/TICKET

    private String action;

    private String entityRef;

    private String payload;   // 事实快照JSON

    private String prevHash;

    private String hash;
}

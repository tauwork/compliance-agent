package com.pcmvp.remediation.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 整改工单：created→assigned→executing→resolved→verified→closed；任意状态可reject→rejected→closed */
@Getter
@Setter
@NoArgsConstructor
@TableName("remediation_ticket")
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long riskEventId;

    private String title;

    private String description;

    private String suggestion;   // AI整改建议（M3 Agent接入后填充）

    private String status;      // created/assigned/executing/resolved/verified/rejected/closed

    private String assignee;

    private String history;     // 流转记录JSON数组

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

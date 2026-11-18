package com.pcmvp.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 风险事件：规则命中生成，闭环联动整改工单 */
@Getter
@Setter
@NoArgsConstructor
@TableName("risk_event")
public class RiskEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventRef;    // 来源事件编号（申报单号）

    private String ruleCode;

    private String ruleName;

    private String severity;    // 红/黄

    private String summary;

    private String detail;      // 触发事实快照JSON

    private String status;      // new/analyzing/ticketed/closed

    private LocalDateTime createdAt;
}

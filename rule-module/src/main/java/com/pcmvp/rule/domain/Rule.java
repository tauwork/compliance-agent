package com.pcmvp.rule.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 审计规则：政策条款 → 规则DSL（JSON条件数组，全部AND）。状态流转：draft→trial→published/rejected */
@Getter
@Setter
@NoArgsConstructor
@TableName("rule")
public class Rule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String severity;      // 红/黄/蓝

    private String dsl;

    private String clauseRef;     // 依据条款（如：演示办法第8条）

    private String scenario;      // 场景隔离：asset/guarantee/lease（评估按场景过滤，防跨场景误命中）

    private Long clauseId;        // 关联条款ID（拆解链路发布时回填，M2）

    private String status;        // draft/trial/published/rejected

    private Boolean enabled;

    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public void touch() {
        this.updatedAt = LocalDateTime.now();
        this.version = (this.version == null ? 1 : this.version) + 1;
    }
}

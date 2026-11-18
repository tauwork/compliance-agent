package com.pcmvp.policy.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 义务清单（拆解五步法第3步产出） */
@Getter
@Setter
@NoArgsConstructor
@TableName("obligation")
public class Obligation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long clauseId;

    private String description;

    private String dept;

    private String threshold;

    private String deadline;

    private String revisedBy;  // ai/human

    private LocalDateTime createdAt;
}

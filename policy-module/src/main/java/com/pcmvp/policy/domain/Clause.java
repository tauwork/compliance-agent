package com.pcmvp.policy.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 政策条款 */
@Getter
@Setter
@NoArgsConstructor
@TableName("clause")
public class Clause {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long policyId;

    private String clauseNo;

    private String content;

    private String clauseType;  // duty/forbid/procedure/authorize

    private String revisedBy;   // ai/human

    private LocalDateTime createdAt;
}

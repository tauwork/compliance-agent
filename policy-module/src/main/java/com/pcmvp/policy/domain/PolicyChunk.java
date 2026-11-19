package com.pcmvp.policy.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 政策文档分片：按条款或段落切开，可改、可删。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("policy_chunk")
public class PolicyChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long policyId;

    private Integer seq;

    private String heading;

    private String content;

    private Integer charCount;

    private Boolean enabled;

    private LocalDateTime createdAt;
}

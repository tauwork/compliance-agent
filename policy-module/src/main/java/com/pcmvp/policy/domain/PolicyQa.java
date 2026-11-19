package com.pcmvp.policy.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 从分片生成或人工录入的问答对，供检索与助手作答。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("policy_qa")
public class PolicyQa {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long policyId;

    private Long chunkId;

    private String question;

    private String answer;

    private String source;

    private LocalDateTime createdAt;
}

package com.pcmvp.llm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** LLM调用留痕：脱敏后入参、响应、时延（M2起记录真实调用） */
@Getter
@Setter
@NoArgsConstructor
@TableName("llm_call_log")
public class LlmCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String model;

    private String purpose;   // decompose/analyze/chat

    private String promptMasked;

    private String response;

    private Long latencyMs;

    private LocalDateTime createdAt;
}

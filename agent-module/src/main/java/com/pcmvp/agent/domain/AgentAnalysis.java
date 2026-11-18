package com.pcmvp.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** AI归因分析（人工门禁对象）：pending → approved（自动派单）/ rejected */
@Getter
@Setter
@NoArgsConstructor
@TableName("agent_analysis")
public class AgentAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long riskEventId;

    private String result;      // 归因分析结论

    private String suggestion;  // 整改建议

    private String model;       // 产出模型（demo-fallback=降级模板）

    private String status;      // pending/approved/rejected

    private LocalDateTime createdAt;
}

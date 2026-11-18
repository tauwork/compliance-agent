package com.pcmvp.policy.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 控制点（拆解五步法第4步产出，映射业务环节与检查方式） */
@Getter
@Setter
@NoArgsConstructor
@TableName("control_point")
public class ControlPoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long obligationId;

    private String bizStage;   // 立项/评估/审批/交易/签约

    private String checkMode;  // 事前/事中/事后

    private String bindFields;

    private LocalDateTime createdAt;
}

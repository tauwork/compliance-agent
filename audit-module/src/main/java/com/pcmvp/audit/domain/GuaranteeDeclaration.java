package com.pcmvp.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 对外担保申报单（场景A：资金条线，演示数据均虚构） */
@Getter
@Setter
@NoArgsConstructor
@TableName("guarantee_declaration")
public class GuaranteeDeclaration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String declNo;

    private String guarantor;          // 担保人（本企业）

    private String guaranteeee;        // 被担保方

    private String guaranteeType;      // 融资担保/履约担保

    private BigDecimal guaranteeAmount; // 本次担保金额（万元）

    private BigDecimal balanceRatio;   // 担保余额占净资产比例（0-1）

    private String guaranteeeeRating;  // AAA/AA/BBB/BB以下/未评级

    private Boolean isHoldingSub;      // 是否本集团控股子企业

    private Boolean counterGuarantee;  // 是否落实反担保

    private String resolutionNo;

    private String submitter;

    private String status;

    private LocalDateTime createdAt;
}

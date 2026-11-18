package com.pcmvp.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 资产处置申报单（业务事件源，演示数据均虚构） */
@Getter
@Setter
@NoArgsConstructor
@TableName("declaration")
public class Declaration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String declNo;          // 申报编号

    private String assetName;

    private String assetCategory;   // 房产/土地/股权/设备

    private BigDecimal bookValue;   // 账面价值（万元）

    private BigDecimal appraisedValue; // 评估值（万元）

    private LocalDate assessmentDate;  // 评估基准日

    private String disposalMode;    // 进场挂牌转让/非公开协议转让

    private Boolean exemption;      // 豁免情形

    private String resolutionNo;    // 决议编号

    private BigDecimal listingPrice; // 拟挂牌价（万元）

    private BigDecimal dealPrice;   // 成交价（万元）

    private String submitter;

    private String status;          // submitted

    private LocalDateTime createdAt;
}

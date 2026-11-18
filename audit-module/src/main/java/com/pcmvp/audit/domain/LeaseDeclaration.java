package com.pcmvp.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 资产租赁申报单（场景C：资源条线，演示数据均虚构） */
@Getter
@Setter
@NoArgsConstructor
@TableName("lease_declaration")
public class LeaseDeclaration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String declNo;

    private String assetName;

    private String assetType;       // 商铺/仓库/土地/广告位/车位

    private String pricingMode;     // 公开竞价/协议定价

    private Integer leaseTermMonths;

    private BigDecimal monthlyRent;     // 租金（万元/月）

    private BigDecimal marketRentRef;   // 市场参考租金（万元/月）

    private Boolean isRenewal;          // 是否到期续签

    private String resolutionNo;

    private String submitter;

    private String status;

    private LocalDateTime createdAt;
}

package com.pcmvp.policy.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 政策法规（M2接入AI拆解流水线） */
@Getter
@Setter
@NoArgsConstructor
@TableName("policy")
public class Policy {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long libraryId;

    private String title;

    private String docNo;

    private String issuer;

    private LocalDate effectDate;

    private LocalDate expireDate;

    /** effective / expired，按失效日期计算，不入库。 */
    @TableField(exist = false)
    private String validity;

    private String version;

    private String status;   // draft/decomposing/decomposed

    private String sourceText;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

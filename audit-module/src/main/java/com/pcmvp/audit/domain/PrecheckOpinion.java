package com.pcmvp.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 前置预审意见单：result=PASS/RED/YELLOW，hits=命中规则JSON数组 */
@Getter
@Setter
@NoArgsConstructor
@TableName("precheck_opinion")
public class PrecheckOpinion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long declarationId;

    /** 场景隔离：asset/guarantee/lease。三张申报表 id 会撞号，必须和 declarationId 一起查。 */
    private String scenario;

    private String result;   // PASS/RED/YELLOW

    private String hits;     // JSON: [{code,name,severity,clauseRef,advice}]

    private LocalDateTime createdAt;
}

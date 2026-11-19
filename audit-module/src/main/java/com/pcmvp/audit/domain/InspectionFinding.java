package com.pcmvp.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 巡检明细：一条已入库申报在本次扫描中的结论。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("inspection_finding")
public class InspectionFinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private String scenario;

    private String declNo;

    private String subject;

    private String result;

    private String hits;

    private String formSnapshot;
}

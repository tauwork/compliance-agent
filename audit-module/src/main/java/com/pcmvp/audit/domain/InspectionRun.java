package com.pcmvp.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 全量巡检批次：一次扫描覆盖指定场景下的全部已入库申报。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("inspection_run")
public class InspectionRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scenario;

    private Integer scanned;

    private Integer hitDecls;

    private Integer redCount;

    private Integer yellowCount;

    private Integer passCount;

    private Integer newRisks;

    private LocalDateTime createdAt;
}

package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("violation_record")
public class ViolationRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long personnelId;
    private String violationItem;
    private BigDecimal fineAmount;
    private Integer paymentStatus;
    private Long sourceAlarmId;
    private LocalDateTime occurredAt;
    private String remark;
    private LocalDateTime createdAt;
}

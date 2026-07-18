package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("alarm_record")
public class AlarmRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String alarmType;
    private String alarmLevel;
    private Long deviceId;
    private Long monitorPointId;
    private Long personnelId;
    private String content;
    private BigDecimal alarmValue;
    private String unit;
    private LocalDateTime occurredAt;
    private Integer status;
    private Long snapshotFileId;
    private LocalDateTime createdAt;
}

package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("spray_record")
public class SprayRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sprayDeviceId;
    private Long taskId;
    private String operationType;
    private String triggerType;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer executeStatus;
}

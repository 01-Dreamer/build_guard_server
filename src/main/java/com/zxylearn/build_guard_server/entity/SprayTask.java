package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("spray_task")
public class SprayTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long sprayDeviceId;
    private LocalDateTime startTime;
    private Integer durationMinutes;
    private Integer cycleValue;
    private String cycleUnit;
    private Integer enabled;
    private LocalDateTime nextRunAt;
}

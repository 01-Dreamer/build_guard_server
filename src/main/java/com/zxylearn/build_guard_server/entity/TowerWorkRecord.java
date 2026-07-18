package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tower_work_record")
public class TowerWorkRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal maxWeight;
    private BigDecimal maxHeight;
    private BigDecimal maxMoment;
    private BigDecimal maxWindSpeed;
}

package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("monitor_rule")
public class MonitorRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long monitorPointId;
    private BigDecimal warnUpper;
    private BigDecimal warnLower;
    private BigDecimal alarmUpper;
    private BigDecimal alarmLower;
    private Integer enabled;
}

package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("monitor_point")
public class MonitorPoint {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private Long deviceId;
    private String metricCode;
    private String metricName;
    private String unit;
    private Integer sort;
    private Integer status;
}

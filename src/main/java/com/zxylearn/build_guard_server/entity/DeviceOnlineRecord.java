package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_online_record")
public class DeviceOnlineRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private Integer onlineStatus;
    private LocalDateTime reportedAt;
    private String source;
}

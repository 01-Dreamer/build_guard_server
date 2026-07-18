package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("device_asset")
public class DeviceAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private Long typeId;
    private Long locationId;
    private String model;
    private String manufacturer;
    private LocalDate installDate;
    private Integer onlineStatus;
    private Integer enabled;
    private Double x;
    private Double y;
    private Long photoFileId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

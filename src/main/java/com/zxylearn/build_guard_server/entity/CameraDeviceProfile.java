package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("camera_device_profile")
public class CameraDeviceProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private String cameraSource;
    private String aiMonitorTypes;
    private Integer enabled;
}

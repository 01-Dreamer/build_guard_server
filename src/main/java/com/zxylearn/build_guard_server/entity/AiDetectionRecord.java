package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_detection_record")
public class AiDetectionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long cameraDeviceId;
    private Long personnelId;
    private String detectType;
    private BigDecimal confidence;
    private Long snapshotFileId;
    @TableField("result_json")
    private String resultJson;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}

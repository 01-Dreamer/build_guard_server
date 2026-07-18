package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_task")
public class AiTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskType;
    private Long deviceId;
    private Long sourceFileId;
    private String requestMessageId;
    private String resultMessageId;
    private Integer taskStatus;
    @TableField("payload_json")
    private String payloadJson;
    @TableField("result_json")
    private String resultJson;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}

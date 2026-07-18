package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mq_message_log")
public class MqMessageLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String topic;
    private String deviceCode;
    private Integer consumeStatus;
    private String errorMessage;
    private LocalDateTime createdAt;
}

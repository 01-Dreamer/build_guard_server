package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_record")
public class EmailRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String receiverEmail;
    private String subject;
    private String content;
    private Integer sendStatus;
    private String bizType;
    private Long bizId;
    private LocalDateTime sentAt;
    private String errorMessage;
    private LocalDateTime createdAt;
}

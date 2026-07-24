package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_resource")
public class FileResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bucket;
    private String objectKey;
    private String url;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    private String bizType;
    private Long bizId;
    private LocalDateTime createdAt;
}

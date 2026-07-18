package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("personnel")
public class Personnel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String phone;
    private String email;
    private Long avatarFileId;
    private String jobTitle;
    private String teamName;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

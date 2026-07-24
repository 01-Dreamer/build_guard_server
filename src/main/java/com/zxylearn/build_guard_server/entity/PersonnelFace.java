package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("personnel_face")
public class PersonnelFace {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long personnelId;
    private String faceRef;
    private LocalDateTime registeredAt;
    private Integer status;
}

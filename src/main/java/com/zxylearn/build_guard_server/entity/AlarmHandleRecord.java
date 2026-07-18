package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alarm_handle_record")
public class AlarmHandleRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long alarmId;
    private String handleBy;
    private String handleContent;
    private LocalDateTime handledAt;
}

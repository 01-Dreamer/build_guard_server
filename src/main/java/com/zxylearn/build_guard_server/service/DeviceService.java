package com.zxylearn.build_guard_server.service;

import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceLocationView;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceTypeView;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeviceService {
    private final JdbcClient jdbcClient;

    public DeviceService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public PageResult<DeviceView> listDevices(String name, Long typeId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        int offset = (safePage - 1) * safePageSize;
        String keyword = name == null || name.isBlank() ? null : "%" + name.trim() + "%";

        long total = jdbcClient.sql("""
                        select count(*)
                        from device_asset d
                        where (:keyword is null or d.name like :keyword)
                          and (:typeId is null or d.type_id = :typeId)
                        """)
                .param("keyword", keyword)
                .param("typeId", typeId)
                .query(Long.class)
                .single();

        List<DeviceView> records = jdbcClient.sql("""
                        select d.id, d.name, d.code, d.type_id, t.name as type_name,
                               d.location_id, l.name as location_name, d.model, d.manufacturer,
                               d.install_date, d.online_status, d.enabled, d.x, d.y
                        from device_asset d
                        left join device_type t on t.id = d.type_id
                        left join device_location l on l.id = d.location_id
                        where (:keyword is null or d.name like :keyword)
                          and (:typeId is null or d.type_id = :typeId)
                        order by d.id desc
                        limit :limit offset :offset
                        """)
                .param("keyword", keyword)
                .param("typeId", typeId)
                .param("limit", safePageSize)
                .param("offset", offset)
                .query((rs, rowNum) -> new DeviceView(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("code"),
                        rs.getLong("type_id"),
                        rs.getString("type_name"),
                        rs.getObject("location_id", Long.class),
                        rs.getString("location_name"),
                        rs.getString("model"),
                        rs.getString("manufacturer"),
                        rs.getDate("install_date") == null ? null : rs.getDate("install_date").toLocalDate(),
                        rs.getInt("online_status"),
                        rs.getInt("enabled"),
                        rs.getObject("x", Double.class),
                        rs.getObject("y", Double.class)
                ))
                .list();

        return new PageResult<>(records, total, safePage, safePageSize);
    }

    public List<DeviceTypeView> listTypes() {
        return jdbcClient.sql("select id, parent_id, name, code, sort, status from device_type order by sort, id")
                .query((rs, rowNum) -> new DeviceTypeView(
                        rs.getLong("id"),
                        rs.getLong("parent_id"),
                        rs.getString("name"),
                        rs.getString("code"),
                        rs.getInt("sort"),
                        rs.getInt("status")
                ))
                .list();
    }

    public List<DeviceLocationView> listLocations() {
        return jdbcClient.sql("select id, area_id, name, code, sort, status from device_location order by sort, id")
                .query((rs, rowNum) -> new DeviceLocationView(
                        rs.getLong("id"),
                        rs.getObject("area_id", Long.class),
                        rs.getString("name"),
                        rs.getString("code"),
                        rs.getInt("sort"),
                        rs.getInt("status")
                ))
                .list();
    }
}

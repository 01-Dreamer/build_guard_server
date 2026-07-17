package com.zxylearn.build_guard_server.service;

import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.PersonnelRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.PersonnelView;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PersonnelService {
    private final JdbcClient jdbcClient;

    public PersonnelService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public PageResult<PersonnelView> listPersonnel(String name, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        int offset = (safePage - 1) * safePageSize;
        String keyword = name == null || name.isBlank() ? null : "%" + name.trim() + "%";

        long total = jdbcClient.sql("""
                        select count(*)
                        from personnel p
                        where (:keyword is null or p.name like :keyword)
                        """)
                .param("keyword", keyword)
                .query(Long.class)
                .single();

        List<PersonnelView> records = jdbcClient.sql("""
                        select p.id, p.name, p.phone, p.email, f.url as avatar_url,
                               p.job_title, p.team_name, p.status, p.created_at
                        from personnel p
                        left join file_resource f on f.id = p.avatar_file_id
                        where (:keyword is null or p.name like :keyword)
                        order by p.id desc
                        limit :limit offset :offset
                        """)
                .param("keyword", keyword)
                .param("limit", safePageSize)
                .param("offset", offset)
                .query((rs, rowNum) -> new PersonnelView(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("email"),
                        rs.getString("avatar_url"),
                        rs.getString("job_title"),
                        rs.getString("team_name"),
                        rs.getInt("status"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ))
                .list();

        return new PageResult<>(records, total, safePage, safePageSize);
    }

    public Long createPersonnel(PersonnelRequest request) {
        return jdbcClient.sql("""
                        insert into personnel(name, phone, email, avatar_file_id, job_title, team_name, status)
                        values (:name, :phone, :email, :avatarFileId, :jobTitle, :teamName, :status)
                        """)
                .param("name", request.name())
                .param("phone", request.phone())
                .param("email", request.email())
                .param("avatarFileId", request.avatarFileId())
                .param("jobTitle", request.jobTitle())
                .param("teamName", request.teamName())
                .param("status", request.status() == null ? 1 : request.status())
                .update()
                .keyHolder()
                .getKeyAs(Long.class);
    }

    public void updatePersonnel(long id, PersonnelRequest request) {
        jdbcClient.sql("""
                        update personnel
                        set name = :name,
                            phone = :phone,
                            email = :email,
                            avatar_file_id = :avatarFileId,
                            job_title = :jobTitle,
                            team_name = :teamName,
                            status = :status
                        where id = :id
                        """)
                .param("id", id)
                .param("name", request.name())
                .param("phone", request.phone())
                .param("email", request.email())
                .param("avatarFileId", request.avatarFileId())
                .param("jobTitle", request.jobTitle())
                .param("teamName", request.teamName())
                .param("status", request.status() == null ? 1 : request.status())
                .update();
    }

    public void deletePersonnel(long id) {
        jdbcClient.sql("delete from personnel where id = :id")
                .param("id", id)
                .update();
    }

    public PageResult<ViolationView> listViolations(String name, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        int offset = (safePage - 1) * safePageSize;
        String keyword = name == null || name.isBlank() ? null : "%" + name.trim() + "%";

        long total = jdbcClient.sql("""
                        select count(*)
                        from violation_record v
                        left join personnel p on p.id = v.personnel_id
                        where (:keyword is null or p.name like :keyword)
                        """)
                .param("keyword", keyword)
                .query(Long.class)
                .single();

        List<ViolationView> records = jdbcClient.sql("""
                        select v.id, v.personnel_id, p.name as personnel_name, v.violation_item,
                               v.fine_amount, v.payment_status, v.occurred_at, v.remark, v.created_at
                        from violation_record v
                        left join personnel p on p.id = v.personnel_id
                        where (:keyword is null or p.name like :keyword)
                        order by v.id desc
                        limit :limit offset :offset
                        """)
                .param("keyword", keyword)
                .param("limit", safePageSize)
                .param("offset", offset)
                .query((rs, rowNum) -> new ViolationView(
                        rs.getLong("id"),
                        rs.getObject("personnel_id", Long.class),
                        rs.getString("personnel_name"),
                        rs.getString("violation_item"),
                        rs.getBigDecimal("fine_amount"),
                        rs.getInt("payment_status"),
                        rs.getTimestamp("occurred_at") == null ? null : rs.getTimestamp("occurred_at").toLocalDateTime(),
                        rs.getString("remark"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ))
                .list();

        return new PageResult<>(records, total, safePage, safePageSize);
    }

    public Long createViolation(ViolationRequest request) {
        BigDecimal fineAmount = request.fineAmount() == null ? BigDecimal.ZERO : request.fineAmount();
        LocalDateTime occurredAt = request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt();
        return jdbcClient.sql("""
                        insert into violation_record(personnel_id, violation_item, fine_amount, payment_status, source_alarm_id, occurred_at, remark)
                        values (:personnelId, :violationItem, :fineAmount, :paymentStatus, :sourceAlarmId, :occurredAt, :remark)
                        """)
                .param("personnelId", request.personnelId())
                .param("violationItem", request.violationItem())
                .param("fineAmount", fineAmount)
                .param("paymentStatus", request.paymentStatus() == null ? 0 : request.paymentStatus())
                .param("sourceAlarmId", request.sourceAlarmId())
                .param("occurredAt", occurredAt)
                .param("remark", request.remark())
                .update()
                .keyHolder()
                .getKeyAs(Long.class);
    }
}

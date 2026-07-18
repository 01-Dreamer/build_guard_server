package com.zxylearn.build_guard_server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.PersonnelRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.PersonnelView;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationView;
import com.zxylearn.build_guard_server.entity.Personnel;
import com.zxylearn.build_guard_server.entity.ViolationRecord;
import com.zxylearn.build_guard_server.mapper.PersonnelMapper;
import com.zxylearn.build_guard_server.mapper.ViolationRecordMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PersonnelService {
    private final PersonnelMapper personnelMapper;
    private final ViolationRecordMapper violationRecordMapper;

    public PersonnelService(PersonnelMapper personnelMapper, ViolationRecordMapper violationRecordMapper) {
        this.personnelMapper = personnelMapper;
        this.violationRecordMapper = violationRecordMapper;
    }

    public PageResult<PersonnelView> listPersonnel(String name, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        IPage<Personnel> result = personnelMapper.selectPage(
                Page.of(safePage, safePageSize),
                Wrappers.<Personnel>lambdaQuery()
                        .like(name != null && !name.isBlank(), Personnel::getName, name == null ? null : name.trim())
                        .orderByDesc(Personnel::getId)
        );

        List<PersonnelView> records = result.getRecords().stream()
                .map(person -> new PersonnelView(
                        person.getId(),
                        person.getName(),
                        person.getPhone(),
                        person.getEmail(),
                        null,
                        person.getJobTitle(),
                        person.getTeamName(),
                        person.getStatus(),
                        person.getCreatedAt()
                ))
                .toList();

        return new PageResult<>(records, result.getTotal(), safePage, safePageSize);
    }

    public Long createPersonnel(PersonnelRequest request) {
        Personnel person = new Personnel();
        applyPersonnelRequest(person, request);
        person.setCreatedAt(LocalDateTime.now());
        personnelMapper.insert(person);
        return person.getId();
    }

    public void updatePersonnel(long id, PersonnelRequest request) {
        Personnel person = new Personnel();
        person.setId(id);
        applyPersonnelRequest(person, request);
        person.setUpdatedAt(LocalDateTime.now());
        personnelMapper.updateById(person);
    }

    public void deletePersonnel(long id) {
        personnelMapper.deleteById(id);
    }

    public PageResult<ViolationView> listViolations(String name, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        List<Long> matchingPersonnelIds = null;
        if (name != null && !name.isBlank()) {
            matchingPersonnelIds = personnelMapper.selectList(Wrappers.<Personnel>lambdaQuery()
                            .like(Personnel::getName, name.trim()))
                    .stream()
                    .map(Personnel::getId)
                    .toList();
            if (matchingPersonnelIds.isEmpty()) {
                return new PageResult<>(List.of(), 0, safePage, safePageSize);
            }
        }

        IPage<ViolationRecord> result = violationRecordMapper.selectPage(
                Page.of(safePage, safePageSize),
                Wrappers.<ViolationRecord>lambdaQuery()
                        .in(matchingPersonnelIds != null, ViolationRecord::getPersonnelId, matchingPersonnelIds)
                        .orderByDesc(ViolationRecord::getId)
        );

        var personnelIds = result.getRecords().stream()
                .map(ViolationRecord::getPersonnelId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Personnel> personnelMap = personnelIds.isEmpty()
                ? Map.of()
                : personnelMapper.selectBatchIds(personnelIds)
                        .stream()
                        .collect(Collectors.toMap(Personnel::getId, Function.identity()));

        List<ViolationView> records = result.getRecords().stream()
                .map(record -> {
                    Personnel person = record.getPersonnelId() == null ? null : personnelMap.get(record.getPersonnelId());
                    return new ViolationView(
                            record.getId(),
                            record.getPersonnelId(),
                            person == null ? null : person.getName(),
                            record.getViolationItem(),
                            record.getFineAmount(),
                            record.getPaymentStatus(),
                            record.getOccurredAt(),
                            record.getRemark(),
                            record.getCreatedAt()
                    );
                })
                .toList();

        return new PageResult<>(records, result.getTotal(), safePage, safePageSize);
    }

    public Long createViolation(ViolationRequest request) {
        ViolationRecord record = new ViolationRecord();
        record.setPersonnelId(request.personnelId());
        record.setViolationItem(request.violationItem());
        record.setFineAmount(request.fineAmount() == null ? BigDecimal.ZERO : request.fineAmount());
        record.setPaymentStatus(request.paymentStatus() == null ? 0 : request.paymentStatus());
        record.setSourceAlarmId(request.sourceAlarmId());
        record.setOccurredAt(request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt());
        record.setRemark(request.remark());
        record.setCreatedAt(LocalDateTime.now());
        violationRecordMapper.insert(record);
        return record.getId();
    }

    private void applyPersonnelRequest(Personnel person, PersonnelRequest request) {
        person.setName(request.name());
        person.setPhone(request.phone());
        person.setEmail(request.email());
        person.setAvatarFileId(request.avatarFileId());
        person.setJobTitle(request.jobTitle());
        person.setTeamName(request.teamName());
        person.setStatus(request.status() == null ? 1 : request.status());
    }
}

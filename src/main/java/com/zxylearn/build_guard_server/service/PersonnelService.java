package com.zxylearn.build_guard_server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.PersonnelRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.PersonnelView;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationFineRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationReviewRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationView;
import com.zxylearn.build_guard_server.entity.AlarmRecord;
import com.zxylearn.build_guard_server.entity.EmailRecord;
import com.zxylearn.build_guard_server.entity.FileResource;
import com.zxylearn.build_guard_server.entity.Personnel;
import com.zxylearn.build_guard_server.entity.PersonnelFace;
import com.zxylearn.build_guard_server.entity.ViolationRecord;
import com.zxylearn.build_guard_server.mapper.AlarmRecordMapper;
import com.zxylearn.build_guard_server.mapper.EmailRecordMapper;
import com.zxylearn.build_guard_server.mapper.FileResourceMapper;
import com.zxylearn.build_guard_server.mapper.PersonnelFaceMapper;
import com.zxylearn.build_guard_server.mapper.PersonnelMapper;
import com.zxylearn.build_guard_server.mapper.ViolationRecordMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
    private final AlarmRecordMapper alarmRecordMapper;
    private final FileResourceMapper fileResourceMapper;
    private final PersonnelFaceMapper personnelFaceMapper;
    private final EmailRecordMapper emailRecordMapper;
    private final FileStorageService fileStorageService;
    private final AiTaskService aiTaskService;
    private final FinePaymentService finePaymentService;
    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String publicBaseUrl;

    public PersonnelService(PersonnelMapper personnelMapper,
                            ViolationRecordMapper violationRecordMapper,
                            AlarmRecordMapper alarmRecordMapper,
                            FileResourceMapper fileResourceMapper,
                            PersonnelFaceMapper personnelFaceMapper,
                            EmailRecordMapper emailRecordMapper,
                            FileStorageService fileStorageService,
                            AiTaskService aiTaskService,
                            FinePaymentService finePaymentService,
                            JavaMailSender mailSender,
                            @Value("${spring.mail.username:}") String mailFrom,
                            @Value("${buildguard.public-base-url:http://110.41.166.11:18080}") String publicBaseUrl) {
        this.personnelMapper = personnelMapper;
        this.violationRecordMapper = violationRecordMapper;
        this.alarmRecordMapper = alarmRecordMapper;
        this.fileResourceMapper = fileResourceMapper;
        this.personnelFaceMapper = personnelFaceMapper;
        this.emailRecordMapper = emailRecordMapper;
        this.fileStorageService = fileStorageService;
        this.aiTaskService = aiTaskService;
        this.finePaymentService = finePaymentService;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom == null ? "" : mailFrom.trim();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
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

        var avatarIds = result.getRecords().stream()
                .map(Personnel::getAvatarFileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, FileResource> avatarMap = avatarIds.isEmpty()
                ? Map.of()
                : fileResourceMapper.selectBatchIds(avatarIds)
                        .stream()
                        .collect(Collectors.toMap(FileResource::getId, Function.identity()));

        List<PersonnelView> records = result.getRecords().stream()
                .map(person -> new PersonnelView(
                        person.getId(),
                        person.getName(),
                        person.getPhone(),
                        person.getEmail(),
                        person.getAvatarFileId() == null || avatarMap.get(person.getAvatarFileId()) == null
                                ? null
                                : avatarMap.get(person.getAvatarFileId()).getUrl(),
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

    public PersonnelView updateAvatar(long id, MultipartFile file) {
        Personnel existing = personnelMapper.selectById(id);
        if (existing == null) {
            throw new com.zxylearn.build_guard_server.common.BusinessException(404, "人员不存在");
        }
        FileResource resource = fileStorageService.saveMultipart(file, "personnel_avatar", id, "personnel/avatar");
        Personnel update = new Personnel();
        update.setId(id);
        update.setAvatarFileId(resource.getId());
        update.setUpdatedAt(LocalDateTime.now());
        personnelMapper.updateById(update);

        upsertFaceRef(id, resource);
        aiTaskService.createFaceRegisterTask(id, resource);

        existing.setAvatarFileId(resource.getId());
        return new PersonnelView(
                existing.getId(),
                existing.getName(),
                existing.getPhone(),
                existing.getEmail(),
                resource.getUrl(),
                existing.getJobTitle(),
                existing.getTeamName(),
                existing.getStatus(),
                existing.getCreatedAt()
        );
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
                        .eq(ViolationRecord::getReviewStatus, 1)
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
        var alarmIds = result.getRecords().stream()
                .map(ViolationRecord::getSourceAlarmId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, AlarmRecord> alarmMap = alarmIds.isEmpty()
                ? Map.of()
                : alarmRecordMapper.selectBatchIds(alarmIds)
                        .stream()
                        .collect(Collectors.toMap(AlarmRecord::getId, Function.identity()));
        var snapshotIds = alarmMap.values().stream()
                .map(AlarmRecord::getSnapshotFileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, FileResource> snapshotMap = snapshotIds.isEmpty()
                ? Map.of()
                : fileResourceMapper.selectBatchIds(snapshotIds)
                        .stream()
                        .collect(Collectors.toMap(FileResource::getId, Function.identity()));

        List<ViolationView> records = result.getRecords().stream()
                .map(record -> {
                    Personnel person = record.getPersonnelId() == null ? null : personnelMap.get(record.getPersonnelId());
                    AlarmRecord alarm = record.getSourceAlarmId() == null ? null : alarmMap.get(record.getSourceAlarmId());
                    FileResource snapshot = alarm == null || alarm.getSnapshotFileId() == null ? null : snapshotMap.get(alarm.getSnapshotFileId());
                    return new ViolationView(
                            record.getId(),
                            record.getPersonnelId(),
                            person == null ? null : person.getName(),
                            record.getViolationItem(),
                            record.getFineAmount(),
                            record.getPaymentStatus(),
                            record.getSourceAlarmId(),
                            snapshot == null ? null : snapshot.getUrl(),
                            record.getReviewStatus(),
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
        record.setReviewStatus(1);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        violationRecordMapper.insert(record);
        return record.getId();
    }

    public void reviewViolation(long id, ViolationReviewRequest request) {
        ViolationRecord update = new ViolationRecord();
        update.setId(id);
        update.setPersonnelId(request.personnelId());
        update.setFineAmount(request.fineAmount() == null ? BigDecimal.ZERO : request.fineAmount());
        update.setPaymentStatus(request.paymentStatus() == null ? 0 : request.paymentStatus());
        update.setRemark(request.remark());
        update.setReviewStatus(1);
        update.setUpdatedAt(LocalDateTime.now());
        violationRecordMapper.updateById(update);
    }

    public void updateViolationFine(long id, ViolationFineRequest request) {
        ViolationRecord existing = violationRecordMapper.selectById(id);
        if (existing == null) {
            throw new com.zxylearn.build_guard_server.common.BusinessException(404, "违规记录不存在");
        }
        ViolationRecord update = new ViolationRecord();
        update.setId(id);
        if (request.personnelId() != null) {
            if (personnelMapper.selectById(request.personnelId()) == null) {
                throw new com.zxylearn.build_guard_server.common.BusinessException(400, "人员不存在");
            }
            update.setPersonnelId(request.personnelId());
        }
        if (request.fineAmount() != null) {
            update.setFineAmount(request.fineAmount());
        }
        if (request.remark() != null) {
            update.setRemark(request.remark().trim());
        }
        if (Boolean.TRUE.equals(request.revoked())) {
            update.setPaymentStatus(3);
        }
        update.setReviewStatus(1);
        update.setUpdatedAt(LocalDateTime.now());
        violationRecordMapper.updateById(update);
    }

    public void remindViolationFine(long id) {
        ViolationRecord violation = violationRecordMapper.selectById(id);
        if (violation == null) {
            throw new com.zxylearn.build_guard_server.common.BusinessException(404, "违规记录不存在");
        }
        if (Integer.valueOf(3).equals(violation.getPaymentStatus())) {
            throw new com.zxylearn.build_guard_server.common.BusinessException(400, "罚款已撤销，不能提醒");
        }
        Personnel person = violation.getPersonnelId() == null ? null : personnelMapper.selectById(violation.getPersonnelId());
        if (person == null || person.getEmail() == null || person.getEmail().isBlank()) {
            throw new com.zxylearn.build_guard_server.common.BusinessException(400, "该人员未配置邮箱");
        }

        String paymentUrl = finePaymentService.publicPaymentPageUrl(id, publicBaseUrl);
        String subject = "BuildGuard罚款支付提醒";
        String content = "您好，您有一条现场违规罚款待处理："
                + violation.getViolationItem()
                + "，罚款金额 "
                + (violation.getFineAmount() == null ? BigDecimal.ZERO : violation.getFineAmount())
                + " 元。请打开以下链接完成支付："
                + paymentUrl;
        String htmlContent = """
                <div style="font-family:Arial,'Microsoft YaHei',sans-serif;color:#24364d;line-height:1.7">
                  <h2 style="margin:0 0 12px">BuildGuard 罚款支付提醒</h2>
                  <p>您好，您有一条现场违规罚款待处理。</p>
                  <p><strong>违规事项：</strong>%s</p>
                  <p><strong>罚款金额：</strong>%s 元</p>
                  <p>
                    <a href="%s" style="display:inline-block;padding:10px 18px;border-radius:8px;background:#3f6fea;color:#fff;text-decoration:none;font-weight:700">
                      打开支付页面
                    </a>
                  </p>
                  <p style="color:#64748b">如果按钮无法打开，请复制链接到浏览器访问：<br>%s</p>
                </div>
                """.formatted(
                escapeHtml(violation.getViolationItem()),
                escapeHtml(String.valueOf(violation.getFineAmount() == null ? BigDecimal.ZERO : violation.getFineAmount())),
                escapeHtml(paymentUrl),
                escapeHtml(paymentUrl)
        );
        EmailRecord record = new EmailRecord();
        record.setReceiverEmail(person.getEmail());
        record.setSubject(subject);
        record.setContent(htmlContent);
        record.setBizType("violation_fine");
        record.setBizId(id);
        record.setCreatedAt(LocalDateTime.now());

        try {
            if (mailFrom.isBlank()) {
                throw new IllegalStateException("未配置发件邮箱");
            }
            var message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    "UTF-8"
            );
            helper.setFrom(mailFrom);
            helper.setTo(person.getEmail());
            helper.setSubject(subject);
            helper.setText(content, htmlContent);
            mailSender.send(message);
            record.setSendStatus(1);
            record.setSentAt(LocalDateTime.now());
        } catch (Exception exception) {
            record.setSendStatus(2);
            record.setErrorMessage(exception.getMessage() == null ? "邮件发送失败" : exception.getMessage());
        }
        emailRecordMapper.insert(record);
        if (Integer.valueOf(2).equals(record.getSendStatus())) {
            throw new com.zxylearn.build_guard_server.common.BusinessException(502, "邮件发送失败，已记录失败原因");
        }
    }

    private void upsertFaceRef(long personnelId, FileResource resource) {
        PersonnelFace existing = personnelFaceMapper.selectOne(Wrappers.<PersonnelFace>lambdaQuery()
                .eq(PersonnelFace::getPersonnelId, personnelId)
                .last("limit 1"));
        if (existing == null) {
            existing = new PersonnelFace();
            existing.setPersonnelId(personnelId);
            existing.setFaceRef("personnel:" + personnelId);
            existing.setRegisteredAt(LocalDateTime.now());
            existing.setStatus(0);
            personnelFaceMapper.insert(existing);
            return;
        }
        existing.setFaceRef("personnel:" + personnelId);
        existing.setRegisteredAt(LocalDateTime.now());
        existing.setStatus(0);
        personnelFaceMapper.updateById(existing);
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

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

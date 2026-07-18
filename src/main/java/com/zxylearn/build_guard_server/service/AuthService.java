package com.zxylearn.build_guard_server.service;

import com.zxylearn.build_guard_server.common.BusinessException;
import com.zxylearn.build_guard_server.dto.LoginRequest;
import com.zxylearn.build_guard_server.dto.LoginResponse;
import com.zxylearn.build_guard_server.entity.AdminUser;
import com.zxylearn.build_guard_server.mapper.AdminUserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private final AdminUserMapper adminUserMapper;
    private final StringRedisTemplate redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(AdminUserMapper adminUserMapper, StringRedisTemplate redisTemplate) {
        this.adminUserMapper = adminUserMapper;
        this.redisTemplate = redisTemplate;
    }

    public LoginResponse login(LoginRequest request, String ip) {
        AdminUser admin = adminUserMapper.selectOne(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getUsername, request.username())
                .last("limit 1"));

        if (admin == null || admin.getStatus() == null || admin.getStatus() != 1) {
            throw new BusinessException(401, "管理员账号不存在或已禁用");
        }
        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(
                    "buildguard:auth:admin_token:" + token,
                    String.valueOf(admin.getId()),
                    Duration.ofHours(12)
            );
        } catch (RuntimeException ignored) {
            // Redis is useful for session lookup, but local development should not fail login if cache is down.
        }

        admin.setLastLoginAt(LocalDateTime.now());
        admin.setLastLoginIp(ip);
        adminUserMapper.updateById(admin);

        return new LoginResponse(
                token,
                admin.getId(),
                admin.getUsername(),
                admin.getRealName(),
                admin.getEmail()
        );
    }

    public Optional<Long> resolveAdminId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            String adminId = redisTemplate.opsForValue().get("buildguard:auth:admin_token:" + token);
            return adminId == null ? Optional.empty() : Optional.of(Long.parseLong(adminId));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}

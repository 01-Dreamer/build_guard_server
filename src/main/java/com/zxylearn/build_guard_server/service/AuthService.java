package com.zxylearn.build_guard_server.service;

import com.zxylearn.build_guard_server.common.BusinessException;
import com.zxylearn.build_guard_server.dto.LoginRequest;
import com.zxylearn.build_guard_server.dto.LoginResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private final JdbcClient jdbcClient;
    private final StringRedisTemplate redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(JdbcClient jdbcClient, StringRedisTemplate redisTemplate) {
        this.jdbcClient = jdbcClient;
        this.redisTemplate = redisTemplate;
    }

    public LoginResponse login(LoginRequest request, String ip) {
        Optional<AdminUserRow> admin = jdbcClient.sql("""
                        select id, username, password_hash, real_name, email, status
                        from admin_user
                        where username = :username
                        limit 1
                        """)
                .param("username", request.username())
                .query((rs, rowNum) -> new AdminUserRow(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("real_name"),
                        rs.getString("email"),
                        rs.getInt("status")
                ))
                .optional();

        if (admin.isEmpty() || admin.get().status() != 1) {
            throw new BusinessException(401, "管理员账号不存在或已禁用");
        }
        if (!passwordEncoder.matches(request.password(), admin.get().passwordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(
                    "buildguard:auth:admin_token:" + token,
                    String.valueOf(admin.get().id()),
                    Duration.ofHours(12)
            );
        } catch (RuntimeException ignored) {
            // Redis is useful for session lookup, but local development should not fail login if cache is down.
        }

        jdbcClient.sql("update admin_user set last_login_at = :now, last_login_ip = :ip where id = :id")
                .param("now", LocalDateTime.now())
                .param("ip", ip)
                .param("id", admin.get().id())
                .update();

        return new LoginResponse(
                token,
                admin.get().id(),
                admin.get().username(),
                admin.get().realName(),
                admin.get().email()
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

    private record AdminUserRow(
            long id,
            String username,
            String passwordHash,
            String realName,
            String email,
            int status
    ) {
    }
}

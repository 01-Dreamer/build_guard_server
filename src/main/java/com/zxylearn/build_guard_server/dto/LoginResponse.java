package com.zxylearn.build_guard_server.dto;

public record LoginResponse(
        String token,
        Long adminId,
        String username,
        String realName,
        String email
) {
}

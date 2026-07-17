package com.zxylearn.build_guard_server.common;

import java.util.List;

public record PageResult<T>(List<T> records, long total, int page, int pageSize) {
}

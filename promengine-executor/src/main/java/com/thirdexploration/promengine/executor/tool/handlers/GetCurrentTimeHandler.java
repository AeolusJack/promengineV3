package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.Map;

@ToolHandler(
        name = "get_current_time",
        description = "获取当前日期时间、时区、时间戳等信息。",
        category = ToolHandler.Category.UTILITY,
        location = ToolHandler.Location.LOCAL,
        version = "1.0.0"
)
@SandboxPolicy(allowedPaths = {})
public class GetCurrentTimeHandler {

    public String execute(
            @ToolParameter(value = "timezone", description = "时区，如 Asia/Shanghai, UTC, America/New_York，默认系统时区", required = false)
            String timezone
    ) {
        ZoneId zoneId;
        if (timezone != null && !timezone.isBlank()) {
            try {
                zoneId = ZoneId.of(timezone);
            } catch (Exception e) {
                return "错误：无效时区 - " + timezone;
            }
        } else {
            zoneId = ZoneId.systemDefault();
        }

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        Map<String, Object> info = new HashMap<>();
        info.put("datetime", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        info.put("date", now.toLocalDate().toString());
        info.put("time", now.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME));
        info.put("timezone", zoneId.toString());
        info.put("timestamp_seconds", now.toEpochSecond());
        info.put("timestamp_millis", now.toInstant().toEpochMilli());
        info.put("day_of_week", now.getDayOfWeek().toString());
        info.put("day_of_year", now.getDayOfYear());
        info.put("week_of_year", now.get(WeekFields.ISO.weekOfWeekBasedYear()));

        StringBuilder sb = new StringBuilder();
        info.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
        return sb.toString();
    }
}
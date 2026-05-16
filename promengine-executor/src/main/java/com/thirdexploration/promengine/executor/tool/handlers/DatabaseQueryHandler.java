package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.SandboxManager;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@ToolHandler(
        name = "db_query",
        description = "执行 SQL 查询（仅支持 SELECT），支持 SQLite 文件或 MySQL 连接。返回 JSON 格式结果。",
        category = ToolHandler.Category.DATA,
        location = ToolHandler.Location.SANDBOX,
        version = "1.0.0"
)
@SandboxPolicy(allowedPaths = {"documents", "projects", "tmp"})
public class DatabaseQueryHandler {

    @Autowired
    private SandboxManager sandboxManager;

    public String execute(
            @ToolParameter(value = "db_type", description = "数据库类型: sqlite 或 mysql", example = "sqlite")
            String dbType,
            @ToolParameter(value = "sqlite_path", description = "SQLite 文件路径（相对于沙箱根），仅当 db_type=sqlite 时使用", required = false)
            String sqlitePath,
            @ToolParameter(value = "mysql_host", description = "MySQL 主机", required = false)
            String mysqlHost,
            @ToolParameter(value = "mysql_port", description = "MySQL 端口", required = false)
            Integer mysqlPort,
            @ToolParameter(value = "mysql_database", description = "MySQL 数据库名", required = false)
            String mysqlDatabase,
            @ToolParameter(value = "mysql_user", description = "MySQL 用户名", required = false)
            String mysqlUser,
            @ToolParameter(value = "mysql_password", description = "MySQL 密码", required = false)
            String mysqlPassword,
            @ToolParameter(value = "sql", description = "SELECT 查询语句", example = "SELECT * FROM users LIMIT 10")
            String sql
    ) throws Exception {
        if (sql == null || sql.trim().isEmpty()) return "错误：缺少 SQL 语句";
        String upperSql = sql.trim().toUpperCase();
        if (!upperSql.startsWith("SELECT")) {
            return "错误：仅支持 SELECT 查询，禁止修改操作";
        }

        Connection conn = null;
        try {
            if ("sqlite".equalsIgnoreCase(dbType)) {
                if (sqlitePath == null) return "错误：缺少 sqlite_path";
                Path dbFile = sandboxManager.resolve(sqlitePath);
                Class.forName("org.sqlite.JDBC");
                conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toString());
            } else if ("mysql".equalsIgnoreCase(dbType)) {
                if (mysqlHost == null || mysqlDatabase == null) return "错误：缺少 MySQL 连接参数";
                int port = (mysqlPort != null) ? mysqlPort : 3306;
                String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC", mysqlHost, port, mysqlDatabase);
                conn = DriverManager.getConnection(url, mysqlUser, mysqlPassword);
            } else {
                return "错误：不支持的 db_type，仅支持 sqlite / mysql";
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                return resultSetToJson(rs);
            }
        } finally {
            if (conn != null) conn.close();
        }
    }

    private String resultSetToJson(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<String> rows = new ArrayList<>();
        while (rs.next()) {
            StringBuilder row = new StringBuilder("{");
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) row.append(",");
                String colName = meta.getColumnName(i);
                Object value = rs.getObject(i);
                String valStr = (value == null) ? "null" : escapeJson(value.toString());
                row.append("\"").append(escapeJson(colName)).append("\":\"").append(valStr).append("\"");
            }
            row.append("}");
            rows.add(row.toString());
        }
        return "[" + String.join(",", rows) + "]";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
package com.mall.common.tools;

import org.junit.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 导出 MySQL 所有表结构 DDL（SHOW CREATE TABLE），写入仓库 db/ddl/mysql/ 目录。
 *
 * 运行方式（在 mall-backend 目录）：
 * mvn -pl mall-common -Dtest=DbDdlExportTest test
 *
 * 可通过系统属性覆盖连接信息：
 * -Ddb.host=192.168.77.100 -Ddb.port=3306 -Ddb.user=root -Ddb.password=root
 */
public class DbDdlExportTest {

    private static final List<String> SCHEMAS = Arrays.asList(
            "mall_admin",
            "mall_oms",
            "mall_pms",
            "mall_sms",
            "mall_ums",
            "mall_wms",
            "sys"
    );

    @Test
    public void exportAllSchemas() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String host = System.getProperty("db.host", "192.168.77.100");
        String port = System.getProperty("db.port", "3306");
        String user = System.getProperty("db.user", "root");
        String password = System.getProperty("db.password", "root");

        String url = "jdbc:mysql://" + host + ":" + port + "/?useUnicode=true&characterEncoding=UTF-8"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

        Path outDir = repoRoot().resolve("db").resolve("ddl").resolve("mysql");
        Files.createDirectories(outDir);

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            for (String schema : SCHEMAS) {
                exportSchema(conn, schema, outDir.resolve(schema + ".sql"));
            }
        }
    }

    private void exportSchema(Connection conn, String schema, Path outFile) throws SQLException, IOException {
        List<String> tables = listTables(conn, schema);
        try (BufferedWriter writer = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            writer.write("-- schema: " + schema);
            writer.newLine();
            writer.write("-- exported_at: " + LocalDateTime.now());
            writer.newLine();
            writer.newLine();

            writer.write("CREATE DATABASE IF NOT EXISTS `" + schema + "`;");
            writer.newLine();
            writer.write("USE `" + schema + "`;");
            writer.newLine();
            writer.newLine();

            for (String table : tables) {
                String ddl = showCreateTable(conn, schema, table);
                writer.write("-- ----------------------------");
                writer.newLine();
                writer.write("-- Table structure for `" + table + "`");
                writer.newLine();
                writer.write("-- ----------------------------");
                writer.newLine();
                writer.write("DROP TABLE IF EXISTS `" + table + "`;");
                writer.newLine();
                writer.write(ddl + ";");
                writer.newLine();
                writer.newLine();
            }
        }
    }

    private List<String> listTables(Connection conn, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        String sql = "SHOW FULL TABLES FROM `" + schema + "` WHERE Table_type = 'BASE TABLE'";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    private String showCreateTable(Connection conn, String schema, String table) throws SQLException {
        String sql = "SHOW CREATE TABLE `" + schema + "`.`" + table + "`";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                throw new SQLException("SHOW CREATE TABLE empty: " + schema + "." + table);
            }
            return rs.getString(2);
        }
    }

    /**
     * 当前模块目录为 mall-backend/mall-common，向上两级到仓库根目录。
     */
    private Path repoRoot() {
        Path moduleDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return moduleDir.getParent().getParent().normalize();
    }
}



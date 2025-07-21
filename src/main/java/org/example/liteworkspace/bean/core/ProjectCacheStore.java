package org.example.liteworkspace.bean.core;

import java.io.File;
import java.sql.*;

public class ProjectCacheStore {

    private final String dbPath;

    public ProjectCacheStore(String projectId) {
        this.dbPath = System.getProperty("user.home") + "/.liteworkspace_cache/" + projectId + "/cache.db";
        new File(dbPath).getParentFile().mkdirs();
        initDatabase();
    }

    private void initDatabase() {
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS file_cache (
                    path TEXT PRIMARY KEY,
                    hash TEXT NOT NULL
                )""");
        } catch (Exception ignored) {}
    }

    public boolean isUnchanged(String filePath, String hash) {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement("SELECT hash FROM file_cache WHERE path=?")) {
            ps.setString(1, filePath);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return hash.equals(rs.getString("hash"));
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void updateCache(String filePath, String hash) {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "REPLACE INTO file_cache(path, hash) VALUES (?, ?)")) {
            ps.setString(1, filePath);
            ps.setString(2, hash);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }
}

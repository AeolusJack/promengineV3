package com.thirdexploration.promengine.runtime.repository;

import com.thirdexploration.promengine.runtime.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public User findByUsername(String username) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT * FROM users WHERE username = ? AND enabled = 1",
                new UserRowMapper(), username);
        } catch (Exception e) {
            return null;
        }
    }

    public User findById(String id) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT * FROM users WHERE id = ?", new UserRowMapper(), id);
        } catch (Exception e) {
            return null;
        }
    }

    public void save(User user) {
        jdbcTemplate.update(
            "INSERT INTO users (id, username, password, nickname, avatar, enabled, created_at) VALUES (?,?,?,?,?,?,?)",
            user.getId(), user.getUsername(), user.getPassword(),
            user.getNickname(), user.getAvatar(), user.isEnabled() ? 1 : 0,
            user.getCreatedAt());
    }

    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            return User.builder()
                .id(rs.getString("id"))
                .username(rs.getString("username"))
                .password(rs.getString("password"))
                .nickname(rs.getString("nickname"))
                .avatar(rs.getString("avatar"))
                .enabled(rs.getInt("enabled") == 1)
                .createdAt(rs.getLong("created_at"))
                .build();
        }
    }
}
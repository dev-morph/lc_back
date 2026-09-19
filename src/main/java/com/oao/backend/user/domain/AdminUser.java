package com.oao.backend.user.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_user")
public class AdminUser extends BaseTimeEntity {

  private static final String ACTIVE_STATUS = "ACTIVE";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;
  private String email;
  private String name;
  private String role;
  private String status;
  private String passwordHash;
  private Instant lastLoginAt;

  protected AdminUser() {}

  public static AdminUser createEmailAdmin(
      String email, String name, String role, String passwordHash) {
    AdminUser adminUser = new AdminUser();
    adminUser.email = email;
    adminUser.name = name;
    adminUser.role = role;
    adminUser.status = ACTIVE_STATUS;
    adminUser.passwordHash = passwordHash;
    return adminUser;
  }

  public void updateEmailLogin(String email, String name, String role, String passwordHash) {
    this.email = email;
    this.name = name;
    this.role = role;
    this.status = ACTIVE_STATUS;
    this.passwordHash = passwordHash;
  }

  public void recordLogin() {
    this.lastLoginAt = Instant.now();
  }

  public boolean isActive() {
    return ACTIVE_STATUS.equals(status);
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getRole() {
    return role;
  }

  public String getStatus() {
    return status;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }
}

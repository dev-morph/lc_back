package com.oao.backend.user.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_account")
public class UserAccount extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  private UserStatus status = UserStatus.ACTIVE;

  @Enumerated(EnumType.STRING)
  private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

  @Enumerated(EnumType.STRING)
  private MemberGrade grade;

  @Enumerated(EnumType.STRING)
  private Gender gender;

  private LocalDate birthDate;
  private Instant approvedAt;
  private Long approvedByAdminId;
  private Instant onboardingCompletedAt;
  private Instant deletedAt;
  private int authVersion;
  private String rejectionReason;

  public int getAuthVersion() {
    return authVersion;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  protected UserAccount() {}

  public static UserAccount createPending() {
    return new UserAccount();
  }

  public void approve(MemberGrade grade, Long adminId) {
    this.approvalStatus = ApprovalStatus.APPROVED;
    this.rejectionReason = null;
    this.grade = grade;
    this.approvedAt = Instant.now();
    this.approvedByAdminId = adminId;
  }

  public void reject(String reason) {
    this.approvalStatus = ApprovalStatus.REJECTED;
    this.rejectionReason = reason;
  }

  public void changeGrade(MemberGrade grade) {
    this.grade = grade;
  }

  public void updateBasicInfo(String name, LocalDate birthDate) {
    this.name = name;
    this.birthDate = birthDate;
  }

  public void updateGender(Gender gender) {
    this.gender = gender;
  }

  public void completeOnboarding() {
    if (this.onboardingCompletedAt == null) {
      this.onboardingCompletedAt = Instant.now();
    }
  }

  public void suspend() {
    this.status = UserStatus.SUSPENDED;
    this.authVersion++;
  }

  public void delete() {
    this.status = UserStatus.DELETED;
    this.deletedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public ApprovalStatus getApprovalStatus() {
    return approvalStatus;
  }

  public MemberGrade getGrade() {
    return grade;
  }

  public Gender getGender() {
    return gender;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getOnboardingCompletedAt() {
    return onboardingCompletedAt;
  }

  public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED
  }

  public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
  }

  public enum MemberGrade {
    S,
    A,
    B
  }

  public enum Gender {
    MALE,
    FEMALE
  }
}

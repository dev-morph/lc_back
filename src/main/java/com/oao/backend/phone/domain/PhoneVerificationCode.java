package com.oao.backend.phone.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "phone_verification_code")
public class PhoneVerificationCode extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;
	private String phoneNumber;
	private String codeHash;

	@Enumerated(EnumType.STRING)
	private PhoneVerificationStatus status;

	private Instant expiresAt;
	private Instant verifiedAt;
	private int attemptCount;
	private int resendCount;
	private Instant lastSentAt;

	protected PhoneVerificationCode() {
	}

	public static PhoneVerificationCode issue(Long userId, String phoneNumber, String codeHash, Instant expiresAt, Instant sentAt) {
		PhoneVerificationCode verification = new PhoneVerificationCode();
		verification.userId = userId;
		verification.phoneNumber = phoneNumber;
		verification.codeHash = codeHash;
		verification.status = PhoneVerificationStatus.PENDING;
		verification.expiresAt = expiresAt;
		verification.lastSentAt = sentAt;
		return verification;
	}

	public void increaseAttempt() {
		this.attemptCount += 1;
	}

	public void verify() {
		this.status = PhoneVerificationStatus.VERIFIED;
		this.verifiedAt = Instant.now();
	}

	public void fail() {
		this.status = PhoneVerificationStatus.FAILED;
	}

	public boolean isPending() {
		return status == PhoneVerificationStatus.PENDING;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public String getCodeHash() {
		return codeHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public Instant getLastSentAt() {
		return lastSentAt;
	}

	public enum PhoneVerificationStatus {
		PENDING,
		VERIFIED,
		EXPIRED,
		FAILED
	}
}

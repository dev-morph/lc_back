package com.oao.backend.user.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "oauth_account")
public class OAuthAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	@Enumerated(EnumType.STRING)
	private OAuthProvider provider;

	private String providerUserId;
	private String email;
	private Instant createdAt;

	protected OAuthAccount() {
	}

	public static OAuthAccount connectKakao(Long userId, String providerUserId, String email) {
		OAuthAccount account = new OAuthAccount();
		account.userId = userId;
		account.provider = OAuthProvider.KAKAO;
		account.providerUserId = providerUserId;
		account.email = email;
		return account;
	}

	public void reconnect(Long userId, String email) {
		this.userId = userId;
		this.email = email;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public Long getUserId() {
		return userId;
	}

	public String getEmail() {
		return email;
	}

	public enum OAuthProvider {
		KAKAO
	}
}

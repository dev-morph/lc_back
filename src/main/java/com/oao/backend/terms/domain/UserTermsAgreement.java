package com.oao.backend.terms.domain;

import com.oao.backend.common.BaseTimeEntity;
import com.oao.backend.terms.domain.TermsDocument.TermsType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_terms_agreement")
public class UserTermsAgreement extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;
	private Long termsDocumentId;

	@Enumerated(EnumType.STRING)
	private TermsType termsType;

	@Column(length = 32)
	private String version;

	private boolean agreed;
	private Instant agreedAt;

	@Column(length = 64)
	private String ipAddress;

	@Column(length = 512)
	private String userAgent;

	protected UserTermsAgreement() {
	}

	public static UserTermsAgreement agree(
		Long userId,
		TermsDocument termsDocument,
		String ipAddress,
		String userAgent
	) {
		UserTermsAgreement agreement = new UserTermsAgreement();
		agreement.userId = userId;
		agreement.termsDocumentId = termsDocument.getId();
		agreement.termsType = termsDocument.getTermsType();
		agreement.version = termsDocument.getVersion();
		agreement.agreed = true;
		agreement.agreedAt = Instant.now();
		agreement.ipAddress = ipAddress;
		agreement.userAgent = userAgent;
		return agreement;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getTermsDocumentId() {
		return termsDocumentId;
	}

	public TermsType getTermsType() {
		return termsType;
	}

	public String getVersion() {
		return version;
	}

	public boolean isAgreed() {
		return agreed;
	}

	public Instant getAgreedAt() {
		return agreedAt;
	}
}

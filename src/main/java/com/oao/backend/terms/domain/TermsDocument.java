package com.oao.backend.terms.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "terms_document")
public class TermsDocument extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	private TermsType termsType;

	@Column(length = 100)
	private String title;

	@Column(length = 32)
	private String version;

	@Column(columnDefinition = "longtext")
	private String content;

	@Column(name = "is_required")
	private boolean required;

	private LocalDate effectiveFrom;

	protected TermsDocument() {
	}

	public Long getId() {
		return id;
	}

	public TermsType getTermsType() {
		return termsType;
	}

	public String getTitle() {
		return title;
	}

	public String getVersion() {
		return version;
	}

	public String getContent() {
		return content;
	}

	public boolean isRequired() {
		return required;
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

	public enum TermsType {
		SERVICE_TERMS,
		PRIVACY_COLLECTION
	}
}

package com.oao.backend.payment.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_transaction")
public class PaymentTransaction extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;
	private Long heartProductId;
	private String provider;
	private String providerTransactionId;
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	private PaymentStatus status = PaymentStatus.READY;

	private Instant approvedAt;
	private String failedReason;

	protected PaymentTransaction() {
	}

	public static PaymentTransaction mockApproved(Long userId, Long heartProductId, BigDecimal amount, String providerTransactionId) {
		PaymentTransaction transaction = new PaymentTransaction();
		transaction.userId = userId;
		transaction.heartProductId = heartProductId;
		transaction.provider = "MOCK";
		transaction.providerTransactionId = providerTransactionId;
		transaction.amount = amount;
		transaction.status = PaymentStatus.APPROVED;
		transaction.approvedAt = Instant.now();
		return transaction;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getHeartProductId() {
		return heartProductId;
	}

	public String getProvider() {
		return provider;
	}

	public String getProviderTransactionId() {
		return providerTransactionId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public enum PaymentStatus {
		READY,
		APPROVED,
		FAILED,
		CANCELED
	}
}

package com.oao.backend.heart.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "heart_transaction")
public class HeartTransaction extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	@Enumerated(EnumType.STRING)
	private HeartTransactionType transactionType;

	private int amount;
	private int balanceAfter;
	private String referenceType;
	private Long referenceId;

	protected HeartTransaction() {
	}

	public static HeartTransaction spend(Long userId, int amount, int balanceAfter, String referenceType, Long referenceId) {
		return create(userId, HeartTransactionType.SPEND, -amount, balanceAfter, referenceType, referenceId);
	}

	public static HeartTransaction charge(Long userId, int amount, int balanceAfter, String referenceType, Long referenceId) {
		return create(userId, HeartTransactionType.CHARGE, amount, balanceAfter, referenceType, referenceId);
	}

	private static HeartTransaction create(
		Long userId,
		HeartTransactionType type,
		int amount,
		int balanceAfter,
		String referenceType,
		Long referenceId
	) {
		HeartTransaction transaction = new HeartTransaction();
		transaction.userId = userId;
		transaction.transactionType = type;
		transaction.amount = amount;
		transaction.balanceAfter = balanceAfter;
		transaction.referenceType = referenceType;
		transaction.referenceId = referenceId;
		return transaction;
	}

	public HeartTransactionType getTransactionType() {
		return transactionType;
	}

	public int getAmount() {
		return amount;
	}

	public int getBalanceAfter() {
		return balanceAfter;
	}

	public String getReferenceType() {
		return referenceType;
	}

	public Long getReferenceId() {
		return referenceId;
	}

	public enum HeartTransactionType {
		CHARGE,
		SPEND,
		REFUND,
		ADJUSTMENT
	}
}

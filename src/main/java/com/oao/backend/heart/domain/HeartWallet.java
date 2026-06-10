package com.oao.backend.heart.domain;

import com.oao.backend.common.BaseTimeEntity;
import com.oao.backend.common.BusinessException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "heart_wallet")
public class HeartWallet extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;
	private int balance;

	protected HeartWallet() {
	}

	public static HeartWallet create(Long userId) {
		HeartWallet wallet = new HeartWallet();
		wallet.userId = userId;
		wallet.balance = 0;
		return wallet;
	}

	public void charge(int amount) {
		if (amount <= 0) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Heart amount must be positive.");
		}
		this.balance += amount;
	}

	public void spend(int amount) {
		if (amount <= 0) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Heart amount must be positive.");
		}
		if (balance < amount) {
			throw new BusinessException(HttpStatus.CONFLICT, "Not enough hearts.");
		}
		this.balance -= amount;
	}

	public int getBalance() {
		return balance;
	}

	public Long getUserId() {
		return userId;
	}
}

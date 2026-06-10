package com.oao.backend.heart.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.heart.domain.HeartTransaction;
import com.oao.backend.heart.domain.HeartWallet;
import com.oao.backend.heart.repository.HeartTransactionRepository;
import com.oao.backend.heart.repository.HeartWalletRepository;
import com.oao.backend.user.repository.UserAccountRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminHeartAdjustmentService {

	private static final String ADMIN_ADJUSTMENT_REFERENCE = "ADMIN_ADJUSTMENT";

	private final UserAccountRepository userAccountRepository;
	private final HeartWalletRepository heartWalletRepository;
	private final HeartTransactionRepository heartTransactionRepository;

	public AdminHeartAdjustmentService(
		UserAccountRepository userAccountRepository,
		HeartWalletRepository heartWalletRepository,
		HeartTransactionRepository heartTransactionRepository
	) {
		this.userAccountRepository = userAccountRepository;
		this.heartWalletRepository = heartWalletRepository;
		this.heartTransactionRepository = heartTransactionRepository;
	}

	@Transactional(readOnly = true)
	public AdminHeartView findHearts(Long userId) {
		ensureUserExists(userId);
		int balance = heartWalletRepository.findByUserId(userId)
			.map(HeartWallet::getBalance)
			.orElse(0);
		List<AdminHeartTransactionView> transactions = heartTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
			.limit(10)
			.map(AdminHeartTransactionView::from)
			.toList();
		return new AdminHeartView(userId, balance, transactions);
	}

	@Transactional
	public AdminHeartView adjust(Long userId, AdminHeartAdjustmentCommand command, Long adminId) {
		ensureUserExists(userId);
		if (command.amount() == null || command.amount() <= 0) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Heart amount must be positive.");
		}
		if (command.amount() > 100000) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Heart amount is too large.");
		}

		HeartWallet wallet = heartWalletRepository.findByUserId(userId)
			.orElseGet(() -> heartWalletRepository.save(HeartWallet.create(userId)));
		HeartTransaction transaction;
		if (command.type() == AdminHeartAdjustmentType.CHARGE) {
			wallet.charge(command.amount());
			transaction = HeartTransaction.charge(userId, command.amount(), wallet.getBalance(), ADMIN_ADJUSTMENT_REFERENCE, adminId);
		} else if (command.type() == AdminHeartAdjustmentType.SPEND) {
			wallet.spend(command.amount());
			transaction = HeartTransaction.spend(userId, command.amount(), wallet.getBalance(), ADMIN_ADJUSTMENT_REFERENCE, adminId);
		} else {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Adjustment type is required.");
		}
		heartTransactionRepository.save(transaction);
		return findHearts(userId);
	}

	private void ensureUserExists(Long userId) {
		if (userId == null || !userAccountRepository.existsById(userId)) {
			throw new BusinessException(HttpStatus.NOT_FOUND, "User not found.");
		}
	}

	public enum AdminHeartAdjustmentType {
		CHARGE,
		SPEND
	}

	public record AdminHeartAdjustmentCommand(AdminHeartAdjustmentType type, Integer amount, String reason) {
	}

	public record AdminHeartView(Long userId, int balance, List<AdminHeartTransactionView> transactions) {
	}

	public record AdminHeartTransactionView(
		String transactionType,
		int amount,
		int balanceAfter,
		String referenceType,
		Long referenceId
	) {

		static AdminHeartTransactionView from(HeartTransaction transaction) {
			return new AdminHeartTransactionView(
				transaction.getTransactionType().name(),
				transaction.getAmount(),
				transaction.getBalanceAfter(),
				transaction.getReferenceType(),
				transaction.getReferenceId()
			);
		}
	}
}

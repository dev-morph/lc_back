package com.oao.backend.heart.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.heart.domain.HeartProduct;
import com.oao.backend.heart.domain.HeartTransaction;
import com.oao.backend.heart.domain.HeartWallet;
import com.oao.backend.heart.repository.HeartProductRepository;
import com.oao.backend.heart.repository.HeartTransactionRepository;
import com.oao.backend.heart.repository.HeartWalletRepository;
import com.oao.backend.payment.domain.PaymentTransaction;
import com.oao.backend.payment.repository.PaymentTransactionRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HeartPurchaseService {

	private static final String ACTIVE_STATUS = "ACTIVE";
	private static final String MOCK_PURCHASE_REFERENCE = "HEART_PURCHASE_MOCK";

	private final HeartProductRepository heartProductRepository;
	private final HeartWalletRepository heartWalletRepository;
	private final HeartTransactionRepository heartTransactionRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;

	public HeartPurchaseService(
		HeartProductRepository heartProductRepository,
		HeartWalletRepository heartWalletRepository,
		HeartTransactionRepository heartTransactionRepository,
		PaymentTransactionRepository paymentTransactionRepository
	) {
		this.heartProductRepository = heartProductRepository;
		this.heartWalletRepository = heartWalletRepository;
		this.heartTransactionRepository = heartTransactionRepository;
		this.paymentTransactionRepository = paymentTransactionRepository;
	}

	@Transactional
	public HeartPurchaseResult purchaseMock(Long userId, Long heartProductId) {
		HeartProduct product = heartProductRepository.findById(heartProductId)
			.filter(foundProduct -> ACTIVE_STATUS.equals(foundProduct.getStatus()))
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Heart product not found."));

		PaymentTransaction payment = paymentTransactionRepository.save(PaymentTransaction.mockApproved(
			userId,
			product.getId(),
			product.getPrice(),
			"mock-" + UUID.randomUUID()
		));

		HeartWallet wallet = heartWalletRepository.findByUserId(userId)
			.orElseGet(() -> heartWalletRepository.save(HeartWallet.create(userId)));
		wallet.charge(product.getHeartAmount());
		heartTransactionRepository.save(HeartTransaction.charge(
			userId,
			product.getHeartAmount(),
			wallet.getBalance(),
			MOCK_PURCHASE_REFERENCE,
			payment.getId()
		));

		return new HeartPurchaseResult(
			userId,
			product.getId(),
			product.getHeartAmount(),
			product.getPrice().longValue(),
			wallet.getBalance(),
			payment.getId(),
			payment.getProvider()
		);
	}

	public record HeartPurchaseResult(
		Long userId,
		Long heartProductId,
		int chargedHearts,
		long paidAmount,
		int balance,
		Long paymentTransactionId,
		String provider
	) {
	}
}

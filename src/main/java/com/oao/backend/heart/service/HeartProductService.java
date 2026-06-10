package com.oao.backend.heart.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.heart.domain.HeartProduct;
import com.oao.backend.heart.repository.HeartProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HeartProductService {

	private static final String ACTIVE_STATUS = "ACTIVE";

	private final HeartProductRepository heartProductRepository;

	public HeartProductService(HeartProductRepository heartProductRepository) {
		this.heartProductRepository = heartProductRepository;
	}

	@Transactional(readOnly = true)
	public List<HeartProductView> findActiveProducts() {
		return heartProductRepository.findByStatusOrderBySortOrderAscHeartAmountAscIdAsc(ACTIVE_STATUS).stream()
			.map(HeartProductView::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<HeartProductView> findAdminProducts() {
		return heartProductRepository.findAllByOrderBySortOrderAscHeartAmountAscIdAsc().stream()
			.map(HeartProductView::from)
			.toList();
	}

	@Transactional
	public List<HeartProductView> updateProducts(List<HeartProductUpdateCommand> commands) {
		if (commands == null || commands.isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Heart products are required.");
		}
		commands.forEach(command -> {
			HeartProduct product = heartProductRepository.findById(command.id())
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Heart product not found."));
			product.update(
				command.name(),
				command.heartAmount(),
				BigDecimal.valueOf(command.price()),
				command.displayDiscountRate(),
				command.status(),
				command.sortOrder(),
				command.recommended()
			);
		});
		return findAdminProducts();
	}

	public record HeartProductUpdateCommand(
		Long id,
		String name,
		int heartAmount,
		long price,
		int displayDiscountRate,
		String status,
		int sortOrder,
		boolean recommended
	) {
	}

	public record HeartProductView(
		Long id,
		String name,
		int heartAmount,
		BigDecimal price,
		BigDecimal unitPrice,
		int displayDiscountRate,
		String status,
		int sortOrder,
		boolean recommended
	) {

		static HeartProductView from(HeartProduct product) {
			return new HeartProductView(
				product.getId(),
				product.getName(),
				product.getHeartAmount(),
				product.getPrice(),
				product.getPrice().divide(BigDecimal.valueOf(product.getHeartAmount()), 0, RoundingMode.HALF_UP),
				product.getDisplayDiscountRate(),
				product.getStatus(),
				product.getSortOrder(),
				product.isRecommended()
			);
		}
	}
}

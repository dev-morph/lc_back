package com.oao.backend.heart.domain;

import com.oao.backend.common.BaseTimeEntity;
import com.oao.backend.common.BusinessException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "heart_product")
public class HeartProduct extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;
	private int heartAmount;
	private BigDecimal price;
	private String status;
	private int displayDiscountRate;
	private int sortOrder;
	private boolean recommended;

	protected HeartProduct() {
	}

	public static HeartProduct create(
		String name,
		int heartAmount,
		BigDecimal price,
		int displayDiscountRate,
		String status,
		int sortOrder,
		boolean recommended
	) {
		HeartProduct product = new HeartProduct();
		product.update(name, heartAmount, price, displayDiscountRate, status, sortOrder, recommended);
		return product;
	}

	public void update(
		String name,
		int heartAmount,
		BigDecimal price,
		int displayDiscountRate,
		String status,
		int sortOrder,
		boolean recommended
	) {
		if (name == null || name.isBlank()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Product name is required.");
		}
		if (heartAmount <= 0) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Heart amount must be positive.");
		}
		if (price == null || price.signum() <= 0) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Product price must be positive.");
		}
		if (displayDiscountRate <= 0 || displayDiscountRate > 100) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Display discount rate must be between 1 and 100.");
		}
		if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Product status must be ACTIVE or INACTIVE.");
		}
		this.name = name.trim();
		this.heartAmount = heartAmount;
		this.price = price;
		this.displayDiscountRate = displayDiscountRate;
		this.status = status;
		this.sortOrder = sortOrder;
		this.recommended = recommended;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getHeartAmount() {
		return heartAmount;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public String getStatus() {
		return status;
	}

	public int getDisplayDiscountRate() {
		return displayDiscountRate;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public boolean isRecommended() {
		return recommended;
	}
}

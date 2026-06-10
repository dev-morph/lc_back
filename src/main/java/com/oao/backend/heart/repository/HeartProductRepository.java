package com.oao.backend.heart.repository;

import com.oao.backend.heart.domain.HeartProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HeartProductRepository extends JpaRepository<HeartProduct, Long> {

	List<HeartProduct> findByStatusOrderBySortOrderAscHeartAmountAscIdAsc(String status);

	List<HeartProduct> findAllByOrderBySortOrderAscHeartAmountAscIdAsc();
}

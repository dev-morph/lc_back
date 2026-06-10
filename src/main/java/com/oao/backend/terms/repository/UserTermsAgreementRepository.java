package com.oao.backend.terms.repository;

import com.oao.backend.terms.domain.UserTermsAgreement;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {

	List<UserTermsAgreement> findByUserId(Long userId);

	List<UserTermsAgreement> findByUserIdAndTermsDocumentIdInAndAgreedTrue(
		Long userId,
		Collection<Long> termsDocumentIds
	);

	boolean existsByUserIdAndTermsDocumentIdAndAgreedTrue(Long userId, Long termsDocumentId);
}

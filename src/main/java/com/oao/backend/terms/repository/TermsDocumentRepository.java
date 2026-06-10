package com.oao.backend.terms.repository;

import com.oao.backend.terms.domain.TermsDocument;
import com.oao.backend.terms.domain.TermsDocument.TermsType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsDocumentRepository extends JpaRepository<TermsDocument, Long> {

	List<TermsDocument> findByRequiredTrueAndEffectiveFromLessThanEqualOrderByTermsTypeAscEffectiveFromDesc(
		LocalDate effectiveFrom
	);

	Optional<TermsDocument> findFirstByTermsTypeAndRequiredTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
		TermsType termsType,
		LocalDate effectiveFrom
	);
}

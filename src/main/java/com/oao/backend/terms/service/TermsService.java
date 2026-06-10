package com.oao.backend.terms.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.terms.domain.TermsDocument;
import com.oao.backend.terms.domain.TermsDocument.TermsType;
import com.oao.backend.terms.domain.UserTermsAgreement;
import com.oao.backend.terms.repository.TermsDocumentRepository;
import com.oao.backend.terms.repository.UserTermsAgreementRepository;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermsService {

	private final TermsDocumentRepository termsDocumentRepository;
	private final UserTermsAgreementRepository userTermsAgreementRepository;

	public TermsService(
		TermsDocumentRepository termsDocumentRepository,
		UserTermsAgreementRepository userTermsAgreementRepository
	) {
		this.termsDocumentRepository = termsDocumentRepository;
		this.userTermsAgreementRepository = userTermsAgreementRepository;
	}

	@Transactional(readOnly = true)
	public List<TermsDocument> currentRequiredTerms() {
		LocalDate today = LocalDate.now();
		return Arrays.stream(TermsType.values())
			.map(termsType -> termsDocumentRepository
				.findFirstByTermsTypeAndRequiredTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
					termsType,
					today
				)
			)
			.flatMap(OptionalStream::stream)
			.toList();
	}

	@Transactional(readOnly = true)
	public TermsAgreementStatus agreementStatus(Long userId) {
		List<TermsDocument> currentRequiredTerms = currentRequiredTerms();
		List<Long> requiredTermIds = currentRequiredTerms.stream()
			.map(TermsDocument::getId)
			.toList();
		Set<Long> agreedTermIds = agreedTermIds(userId, requiredTermIds);
		List<TermsDocument> missingRequiredTerms = currentRequiredTerms.stream()
			.filter(termsDocument -> !agreedTermIds.contains(termsDocument.getId()))
			.toList();
		return new TermsAgreementStatus(missingRequiredTerms.isEmpty(), missingRequiredTerms);
	}

	@Transactional
	public TermsAgreementStatus agree(
		Long userId,
		List<TermsAgreementCommand> commands,
		String ipAddress,
		String userAgent
	) {
		if (commands == null || commands.isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Terms agreements are required.");
		}

		commands.stream()
			.filter(command -> !command.agreed())
			.findFirst()
			.ifPresent(command -> {
				throw new BusinessException(HttpStatus.BAD_REQUEST, "Only agreed terms can be saved.");
			});

		List<Long> requestedTermIds = commands.stream()
			.map(TermsAgreementCommand::termsDocumentId)
			.distinct()
			.toList();
		Map<Long, TermsDocument> termsDocumentById = termsDocumentRepository.findAllById(requestedTermIds)
			.stream()
			.collect(Collectors.toMap(TermsDocument::getId, Function.identity()));

		List<Long> missingTermIds = requestedTermIds.stream()
			.filter(termsDocumentId -> !termsDocumentById.containsKey(termsDocumentId))
			.toList();
		if (!missingTermIds.isEmpty()) {
			throw new BusinessException(HttpStatus.NOT_FOUND, "Terms document not found.");
		}

		Set<Long> alreadyAgreedTermIds = agreedTermIds(userId, requestedTermIds);
		List<UserTermsAgreement> newAgreements = requestedTermIds.stream()
			.filter(termsDocumentId -> !alreadyAgreedTermIds.contains(termsDocumentId))
			.map(termsDocumentId -> UserTermsAgreement.agree(
				userId,
				termsDocumentById.get(termsDocumentId),
				ipAddress,
				userAgent
			))
			.toList();

		userTermsAgreementRepository.saveAll(newAgreements);
		return agreementStatus(userId);
	}

	private Set<Long> agreedTermIds(Long userId, Collection<Long> termsDocumentIds) {
		if (termsDocumentIds.isEmpty()) {
			return Set.of();
		}
		return userTermsAgreementRepository
			.findByUserIdAndTermsDocumentIdInAndAgreedTrue(userId, termsDocumentIds)
			.stream()
			.map(UserTermsAgreement::getTermsDocumentId)
			.collect(Collectors.toSet());
	}

	public record TermsAgreementCommand(Long termsDocumentId, boolean agreed) {
	}

	public record TermsAgreementStatus(boolean requiredTermsAgreed, List<TermsDocument> missingRequiredTerms) {
	}

	private static final class OptionalStream {

		private OptionalStream() {
		}

		static <T> java.util.stream.Stream<T> stream(java.util.Optional<T> optional) {
			return optional.stream();
		}
	}
}

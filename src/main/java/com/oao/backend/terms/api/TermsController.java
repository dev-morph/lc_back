package com.oao.backend.terms.api;

import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.common.BusinessException;
import com.oao.backend.terms.domain.TermsDocument;
import com.oao.backend.terms.service.TermsService;
import com.oao.backend.terms.service.TermsService.TermsAgreementCommand;
import com.oao.backend.terms.service.TermsService.TermsAgreementStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/terms")
public class TermsController {

	private final TermsService termsService;

	public TermsController(TermsService termsService) {
		this.termsService = termsService;
	}

	@GetMapping("/current")
	ApiResponse<List<TermsDocumentResponse>> currentTerms() {
		return ApiResponse.ok(
			termsService.currentRequiredTerms()
				.stream()
				.map(TermsDocumentResponse::from)
				.toList()
		);
	}

	@GetMapping("/me/status")
	ApiResponse<TermsAgreementStatusResponse> myAgreementStatus(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		Long userId = resolveUserId(principal, headerUserId);
		TermsAgreementStatus status = termsService.agreementStatus(userId);
		return ApiResponse.ok(TermsAgreementStatusResponse.from(userId, status));
	}

	@PostMapping("/me/agreements")
	ApiResponse<TermsAgreementSaveResponse> agree(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
		@Valid @RequestBody TermsAgreementsRequest request,
		HttpServletRequest servletRequest
	) {
		Long userId = resolveUserId(principal, headerUserId);
		TermsAgreementStatus status = termsService.agree(
			userId,
			request.toCommands(),
			clientIp(servletRequest),
			trimToLength(servletRequest.getHeader("User-Agent"), 512)
		);
		return ApiResponse.ok(new TermsAgreementSaveResponse(status.requiredTermsAgreed()));
	}

	private Long resolveUserId(KakaoPrincipal principal, Long headerUserId) {
		if (principal != null) {
			return principal.getUserId();
		}
		if (headerUserId != null) {
			return headerUserId;
		}
		throw new BusinessException(HttpStatus.UNAUTHORIZED, "Login is required.");
	}

	private String clientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return trimToLength(forwardedFor.split(",")[0].trim(), 64);
		}
		return trimToLength(request.getRemoteAddr(), 64);
	}

	private String trimToLength(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	record TermsAgreementsRequest(@NotEmpty List<@Valid TermsAgreementRequest> agreements) {

		List<TermsAgreementCommand> toCommands() {
			return agreements.stream()
				.map(agreement -> new TermsAgreementCommand(agreement.termsDocumentId(), agreement.agreed()))
				.toList();
		}
	}

	record TermsAgreementRequest(@NotNull Long termsDocumentId, @NotNull Boolean agreed) {
	}

	record TermsDocumentResponse(
		Long id,
		String termsType,
		String title,
		String version,
		boolean required,
		LocalDate effectiveFrom,
		String content
	) {

		static TermsDocumentResponse from(TermsDocument termsDocument) {
			return new TermsDocumentResponse(
				termsDocument.getId(),
				termsDocument.getTermsType().name(),
				termsDocument.getTitle(),
				termsDocument.getVersion(),
				termsDocument.isRequired(),
				termsDocument.getEffectiveFrom(),
				termsDocument.getContent()
			);
		}
	}

	record TermsAgreementStatusResponse(
		boolean authenticated,
		Long userId,
		boolean requiredTermsAgreed,
		List<MissingRequiredTermsResponse> missingRequiredTerms
	) {

		static TermsAgreementStatusResponse from(Long userId, TermsAgreementStatus status) {
			return new TermsAgreementStatusResponse(
				true,
				userId,
				status.requiredTermsAgreed(),
				status.missingRequiredTerms()
					.stream()
					.map(MissingRequiredTermsResponse::from)
					.toList()
			);
		}
	}

	record MissingRequiredTermsResponse(Long id, String termsType, String title, String version) {

		static MissingRequiredTermsResponse from(TermsDocument termsDocument) {
			return new MissingRequiredTermsResponse(
				termsDocument.getId(),
				termsDocument.getTermsType().name(),
				termsDocument.getTitle(),
				termsDocument.getVersion()
			);
		}
	}

	record TermsAgreementSaveResponse(boolean requiredTermsAgreed) {
	}
}

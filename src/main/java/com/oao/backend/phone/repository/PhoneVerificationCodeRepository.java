package com.oao.backend.phone.repository;

import com.oao.backend.phone.domain.PhoneVerificationCode;
import com.oao.backend.phone.domain.PhoneVerificationCode.PhoneVerificationStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhoneVerificationCodeRepository extends JpaRepository<PhoneVerificationCode, Long> {

	Optional<PhoneVerificationCode> findFirstByUserIdAndStatusOrderByCreatedAtDescIdDesc(
		Long userId,
		PhoneVerificationStatus status
	);

	long countByUserIdAndLastSentAtGreaterThanEqual(Long userId, Instant from);

	long countByPhoneNumberAndLastSentAtGreaterThanEqual(String phoneNumber, Instant from);
}

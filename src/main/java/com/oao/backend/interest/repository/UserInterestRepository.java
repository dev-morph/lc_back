package com.oao.backend.interest.repository;

import com.oao.backend.interest.domain.UserInterest;
import com.oao.backend.interest.domain.UserInterest.InterestStatus;
import com.oao.backend.interest.domain.UserInterest.InterestType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {

	Optional<UserInterest> findBySenderUserIdAndReceiverUserId(Long senderUserId, Long receiverUserId);

	boolean existsBySenderUserIdAndReceiverUserIdAndStatus(Long senderUserId, Long receiverUserId, InterestStatus status);

	Optional<UserInterest> findBySenderUserIdAndReceiverUserIdAndStatus(
		Long senderUserId,
		Long receiverUserId,
		InterestStatus status
	);

	boolean existsBySenderUserIdAndReceiverUserIdAndStatusAndInterestType(
		Long senderUserId,
		Long receiverUserId,
		InterestStatus status,
		InterestType interestType
	);

	Optional<UserInterest> findBySenderUserIdAndReceiverUserIdAndStatusAndInterestType(
		Long senderUserId,
		Long receiverUserId,
		InterestStatus status,
		InterestType interestType
	);

	List<UserInterest> findByReceiverUserIdAndStatusOrderByCreatedAtDescIdDesc(Long receiverUserId, InterestStatus status);

	List<UserInterest> findByReceiverUserIdAndStatusAndInterestTypeOrderByCreatedAtDescIdDesc(
		Long receiverUserId,
		InterestStatus status,
		InterestType interestType
	);

	List<UserInterest> findBySenderUserIdAndStatusOrderByCreatedAtDescIdDesc(Long senderUserId, InterestStatus status);
}

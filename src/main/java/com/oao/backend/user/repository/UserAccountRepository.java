package com.oao.backend.user.repository;

import com.oao.backend.user.domain.UserAccount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

	List<UserAccount> findByApprovalStatus(UserAccount.ApprovalStatus approvalStatus);
}

package com.oao.backend.user.repository;

import com.oao.backend.user.domain.UserVerificationDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserVerificationDocumentRepository extends JpaRepository<UserVerificationDocument, Long> {

	List<UserVerificationDocument> findByUserId(Long userId);
}

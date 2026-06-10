package com.oao.backend.user.repository;

import com.oao.backend.user.domain.OAuthAccount;
import com.oao.backend.user.domain.OAuthAccount.OAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

	Optional<OAuthAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

	Optional<OAuthAccount> findFirstByUserId(Long userId);
}

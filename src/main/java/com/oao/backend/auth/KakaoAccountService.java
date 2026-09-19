package com.oao.backend.auth;

import com.oao.backend.common.UserLocks;
import com.oao.backend.user.domain.OAuthAccount;
import com.oao.backend.user.domain.OAuthAccount.OAuthProvider;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.repository.OAuthAccountRepository;
import com.oao.backend.user.repository.UserAccountRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Called only after Spring OAuth has verified the provider identity. Email equality is never an
 * account merge.
 */
@Service
public class KakaoAccountService {
  private final OAuthAccountRepository accounts;
  private final UserAccountRepository users;
  private final UserLocks locks;

  public KakaoAccountService(
      OAuthAccountRepository accounts, UserAccountRepository users, UserLocks locks) {
    this.accounts = accounts;
    this.users = users;
    this.locks = locks;
  }

  @Transactional
  public UserAccount resolveVerifiedIdentity(String providerId, String email, Long linkUserId) {
    if (linkUserId != null) locks.lock(linkUserId);
    var account =
        accounts.findByProviderAndProviderUserId(OAuthProvider.KAKAO, providerId).orElse(null);
    if (account != null && linkUserId != null && !account.getUserId().equals(linkUserId))
      throw new OAuth2AuthenticationException("이 카카오 계정은 다른 회원에게 연결되어 있습니다.");
    if (account == null) {
      var target =
          linkUserId == null
              ? users.saveAndFlush(UserAccount.createPending())
              : users
                  .findById(linkUserId)
                  .orElseThrow(() -> new OAuth2AuthenticationException("계정을 찾을 수 없습니다."));
      if (target.getStatus() != UserAccount.UserStatus.ACTIVE)
        throw new OAuth2AuthenticationException("이용할 수 없는 계정입니다.");
      if (linkUserId != null && accounts.findFirstByUserId(linkUserId).isPresent())
        throw new OAuth2AuthenticationException("이미 카카오 계정이 연결되어 있습니다.");
      account = accounts.save(OAuthAccount.connectKakao(target.getId(), providerId, email));
    }
    var user =
        users
            .findById(account.getUserId())
            .orElseThrow(() -> new OAuth2AuthenticationException("계정을 찾을 수 없습니다."));
    if (user.getStatus() != UserAccount.UserStatus.ACTIVE)
      throw new OAuth2AuthenticationException("이용할 수 없는 계정입니다.");
    return user;
  }
}

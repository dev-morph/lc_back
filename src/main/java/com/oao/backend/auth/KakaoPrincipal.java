package com.oao.backend.auth;

import com.oao.backend.user.domain.UserAccount.ApprovalStatus;
import com.oao.backend.user.domain.UserAccount.MemberGrade;
import java.util.Collection;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class KakaoPrincipal implements OAuth2User {

	private final Long userId;
	private final String providerUserId;
	private final String email;
	private final String nickname;
	private final ApprovalStatus approvalStatus;
	private final MemberGrade grade;
	private final Map<String, Object> attributes;
	private final Collection<? extends GrantedAuthority> authorities;

	public KakaoPrincipal(
		Long userId,
		String providerUserId,
		String email,
		String nickname,
		ApprovalStatus approvalStatus,
		MemberGrade grade,
		Map<String, Object> attributes,
		Collection<? extends GrantedAuthority> authorities
	) {
		this.userId = userId;
		this.providerUserId = providerUserId;
		this.email = email;
		this.nickname = nickname;
		this.approvalStatus = approvalStatus;
		this.grade = grade;
		this.attributes = attributes;
		this.authorities = authorities;
	}

	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getName() {
		return providerUserId;
	}

	public Long getUserId() {
		return userId;
	}

	public String getEmail() {
		return email;
	}

	public String getNickname() {
		return nickname;
	}

	public ApprovalStatus getApprovalStatus() {
		return approvalStatus;
	}

	public MemberGrade getGrade() {
		return grade;
	}
}

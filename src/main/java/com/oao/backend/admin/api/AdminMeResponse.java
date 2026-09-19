package com.oao.backend.admin.api;

import com.oao.backend.user.domain.AdminUser;

public record AdminMeResponse(boolean admin, Long adminId, String name, String role) {

	public static AdminMeResponse from(AdminUser admin) {
		if (admin == null) {
			return new AdminMeResponse(false, null, null, null);
		}
		return new AdminMeResponse(true, admin.getId(), admin.getName(), admin.getRole());
	}
}

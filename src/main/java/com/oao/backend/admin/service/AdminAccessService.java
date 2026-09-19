package com.oao.backend.admin.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.user.domain.AdminUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AdminAccessService {

  private final AdminAuthService adminAuthService;

  public AdminAccessService(AdminAuthService adminAuthService) {
    this.adminAuthService = adminAuthService;
  }

  public AdminUser requireActiveAdmin(HttpServletRequest request) {
    AdminUser admin = adminAuthService.findCurrentAdminOrNull(request);
    if (admin == null) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "Admin access is required.");
    }
    return admin;
  }

  public AdminUser findActiveAdminOrNull(HttpServletRequest request) {
    return adminAuthService.findCurrentAdminOrNull(request);
  }
}

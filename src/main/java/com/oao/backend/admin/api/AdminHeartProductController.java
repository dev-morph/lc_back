package com.oao.backend.admin.api;

import com.oao.backend.admin.service.AdminAccessService;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.heart.service.HeartProductService;
import com.oao.backend.heart.service.HeartProductService.HeartProductUpdateCommand;
import com.oao.backend.heart.service.HeartProductService.HeartProductView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/settings/heart-products")
public class AdminHeartProductController {

  private final AdminAccessService adminAccessService;
  private final HeartProductService heartProductService;

  public AdminHeartProductController(
      AdminAccessService adminAccessService, HeartProductService heartProductService) {
    this.adminAccessService = adminAccessService;
    this.heartProductService = heartProductService;
  }

  @GetMapping
  ApiResponse<List<HeartProductView>> products(HttpServletRequest servletRequest) {
    adminAccessService.requireActiveAdmin(servletRequest);
    return ApiResponse.ok(heartProductService.findAdminProducts());
  }

  @PutMapping
  ApiResponse<List<HeartProductView>> updateProducts(
      HttpServletRequest servletRequest, @Valid @RequestBody UpdateHeartProductsRequest request) {
    adminAccessService.requireActiveAdmin(servletRequest);
    return ApiResponse.ok(heartProductService.updateProducts(request.toCommands()));
  }

  record UpdateHeartProductsRequest(@NotNull List<@Valid HeartProductRequest> products) {

    List<HeartProductUpdateCommand> toCommands() {
      return products.stream()
          .map(
              product ->
                  new HeartProductUpdateCommand(
                      product.id(),
                      product.name(),
                      product.heartAmount(),
                      product.price(),
                      product.displayDiscountRate(),
                      product.status(),
                      product.sortOrder(),
                      product.recommended()))
          .toList();
    }
  }

  record HeartProductRequest(
      @NotNull Long id,
      @NotBlank String name,
      @Positive int heartAmount,
      @Positive long price,
      @Positive int displayDiscountRate,
      @NotBlank String status,
      int sortOrder,
      boolean recommended) {}
}

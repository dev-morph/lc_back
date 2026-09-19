package com.oao.backend.user.api;

import com.oao.backend.admin.service.AdminAccessService;
import com.oao.backend.auth.CurrentUser;
import com.oao.backend.common.*;
import com.oao.backend.user.service.VerificationReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class VerificationReviewController {
  private final CurrentUser current;
  private final VerificationReviewService service;
  private final AdminAccessService admin;

  public VerificationReviewController(
      CurrentUser current, VerificationReviewService service, AdminAccessService admin) {
    this.current = current;
    this.service = service;
    this.admin = admin;
  }

  @GetMapping("/me/documents")
  ApiResponse<?> docs(HttpServletRequest r) {
    return ApiResponse.ok(service.documents(current.require(r)));
  }

  @GetMapping("/me/photo-reviews")
  ApiResponse<?> photos(HttpServletRequest r) {
    return ApiResponse.ok(service.myPhotos(current.require(r)));
  }

  @PostMapping("/me/documents")
  ApiResponse<?> upload(
      HttpServletRequest r, @RequestParam String type, @RequestParam MultipartFile file) {
    service.upload(current.require(r), type, file);
    return ApiResponse.ok();
  }

  @DeleteMapping("/me/documents/{id}")
  ApiResponse<?> delete(HttpServletRequest r, @PathVariable Long id) {
    service.delete(current.require(r), id);
    return ApiResponse.ok();
  }

  @GetMapping("/me/documents/{id}/file")
  ResponseEntity<Resource> file(HttpServletRequest r, @PathVariable Long id) {
    return download(service.file(id, current.require(r), false));
  }

  @GetMapping("/admin/reviews/documents")
  ApiResponse<?> queue(HttpServletRequest r) {
    admin.requireActiveAdmin(r);
    return ApiResponse.ok(service.queue());
  }

  @GetMapping("/admin/reviews/photos")
  ApiResponse<?> queuePhotos(HttpServletRequest r) {
    admin.requireActiveAdmin(r);
    return ApiResponse.ok(service.photos());
  }

  @GetMapping("/admin/reviews/documents/{id}/file")
  ResponseEntity<Resource> adminFile(HttpServletRequest r, @PathVariable Long id) {
    admin.requireActiveAdmin(r);
    return download(service.file(id, null, true));
  }

  @PatchMapping("/admin/reviews/{kind}/{id}")
  ApiResponse<?> review(
      HttpServletRequest r,
      @PathVariable String kind,
      @PathVariable Long id,
      @Valid @RequestBody Review t) {
    Long a = admin.requireActiveAdmin(r).getId();
    if (!java.util.Set.of("photos", "documents").contains(kind))
      throw new BusinessException(HttpStatus.BAD_REQUEST, "대상을 확인해주세요.");
    service.review(id, kind.equals("photos"), t.status(), t.reason(), a);
    return ApiResponse.ok();
  }

  private ResponseEntity<Resource> download(Resource resource) {
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .header(
            "Content-Disposition",
            "attachment; filename=verification"
                + (resource.getFilename().endsWith(".pdf")
                    ? ".pdf"
                    : resource.getFilename().endsWith(".png") ? ".png" : ".jpg"))
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(resource);
  }

  record Review(@NotBlank String status, @Size(max = 512) String reason) {}
}

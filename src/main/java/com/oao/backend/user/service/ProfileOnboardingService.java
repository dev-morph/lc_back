package com.oao.backend.user.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.domain.UserVerificationDocument;
import com.oao.backend.user.repository.UserAccountRepository;
import com.oao.backend.user.repository.UserVerificationDocumentRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileOnboardingService {

  private static final String EMPLOYMENT = "EMPLOYMENT";
  private static final String EDUCATION = "EDUCATION";

  private final UserAccountRepository users;
  private final UserVerificationDocumentRepository documents;
  private final UserProfileService profiles;
  private final ProfileIntroPhotoService introPhotos;

  public ProfileOnboardingService(
      UserAccountRepository users,
      UserVerificationDocumentRepository documents,
      UserProfileService profiles,
      ProfileIntroPhotoService introPhotos) {
    this.users = users;
    this.documents = documents;
    this.profiles = profiles;
    this.introPhotos = introPhotos;
  }

  @Transactional(readOnly = true)
  public OnboardingStatus status(Long userId) {
    UserAccount user = findUser(userId);
    boolean profileCompleted = profiles.findProfile(userId).profileCompleted();
    boolean introPhotoCompleted = introPhotos.findIntroPhoto(userId).completed();
    List<UserVerificationDocument> submittedDocuments = documents.findByUserId(userId);
    boolean employmentSubmitted = hasActiveSubmission(submittedDocuments, EMPLOYMENT);
    boolean educationSubmitted = hasActiveSubmission(submittedDocuments, EDUCATION);
    return new OnboardingStatus(
        profileCompleted,
        introPhotoCompleted,
        employmentSubmitted,
        educationSubmitted,
        user.getOnboardingCompletedAt() != null);
  }

  @Transactional
  public OnboardingStatus complete(Long userId) {
    UserAccount user = findUser(userId);
    if (user.getOnboardingCompletedAt() != null) {
      return status(userId);
    }

    OnboardingStatus current = status(userId);
    if (!current.profileCompleted()) {
      throw incomplete("기본 프로필을 먼저 완성해주세요.");
    }
    if (!current.introPhotoCompleted()) {
      throw incomplete("소개와 프로필 사진을 먼저 등록해주세요.");
    }
    user.completeOnboarding();
    users.save(user);
    return new OnboardingStatus(true, true, current.employmentDocumentSubmitted(),
        current.educationDocumentSubmitted(), true);
  }

  private UserAccount findUser(Long userId) {
    return users
        .findById(userId)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
  }

  private boolean hasActiveSubmission(
      List<UserVerificationDocument> submittedDocuments, String documentType) {
    return submittedDocuments.stream()
        .filter(document -> documentType.equals(document.getDocumentType()))
        .max(java.util.Comparator.comparing(UserVerificationDocument::getId))
        .map(
            document ->
                document.getReviewStatus() != UserVerificationDocument.ReviewStatus.REJECTED)
        .orElse(false);
  }

  private BusinessException incomplete(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, message);
  }

  public record OnboardingStatus(
      boolean profileCompleted,
      boolean introPhotoCompleted,
      boolean employmentDocumentSubmitted,
      boolean educationDocumentSubmitted,
      boolean onboardingCompleted) {}
}

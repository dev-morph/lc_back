package com.oao.backend;

import static org.assertj.core.api.Assertions.*;

import com.oao.backend.common.BusinessException;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.repository.UserAccountRepository;
import com.oao.backend.user.service.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = {
    "spring.datasource.url=${OAO_PROFILE_DB_URL:jdbc:h2:mem:profilemanagement;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.driver-class-name=${OAO_TEST_DB_DRIVER:org.h2.Driver}",
    "spring.datasource.username=${OAO_TEST_DB_USER:sa}", "spring.datasource.password=${OAO_TEST_DB_PASSWORD:}",
    "spring.flyway.enabled=${OAO_TEST_FLYWAY:false}", "spring.jpa.hibernate.ddl-auto=none",
    "spring.config.import=", "oao.admin-bootstrap.enabled=false", "oao.dev-tools.enabled=false",
    "oao.alimtalk.enabled=false", "oao.admin.bootstrap.email=", "oao.admin.bootstrap.password="
})
@ContextConfiguration(initializers = WorkflowDatabaseInitializer.class)
class ProfileManagementIntegrationTests {
  @Autowired JdbcTemplate db;
  @Autowired UserAccountRepository users;
  @Autowired VerificationBadgeService badges;
  @Autowired VerificationReviewService reviews;
  @Autowired UserProfileService profiles;
  @Autowired ProfileIntroPhotoService photos;
  @Autowired com.oao.backend.matching.service.MatchReadService matches;
  @Autowired com.oao.backend.interest.service.UserInterestService interests;

  @Test void latestApprovalIsIndependentAndBoundToTheSubmittedProfileValue() {
    long user = member("FEMALE");
    long job = submit(user, "EMPLOYMENT");
    long school = submit(user, "EDUCATION");
    assertThat(badges.forUser(user)).isEqualTo(VerificationBadgeService.Badges.NONE);
    reviews.review(job, false, "APPROVED", null, null);
    reviews.review(school, false, "APPROVED", null, null);
    assertThat(badges.forUser(user)).isEqualTo(new VerificationBadgeService.Badges(true, true));
    profiles.updateProfile(user, command("약사", "대학교 졸업"));
    assertThat(badges.forUser(user).employmentVerified()).isTrue();
    long pending = submit(user, "EMPLOYMENT");
    assertThat(badges.forUser(user)).isEqualTo(new VerificationBadgeService.Badges(false, true));
    reviews.review(pending, false, "REJECTED", "내용 확인 필요", null);
    assertThat(badges.forUser(user).employmentVerified()).isFalse();
    profiles.updateProfile(user, command("연구원", "대학교 졸업"));
    assertThatThrownBy(() -> reviews.review(job, false, "APPROVED", null, null))
        .isInstanceOf(BusinessException.class).hasMessageContaining("변경");
    profiles.updateProfile(user, command("약사", "대학교 졸업"));
    assertThat(badges.forUser(user)).isEqualTo(new VerificationBadgeService.Badges(false, true));
    long replacement = submit(user, "EMPLOYMENT");
    reviews.review(replacement, false, "APPROVED", null, null);
    assertThat(badges.forUser(user).employmentVerified()).isTrue();
    profiles.updateProfile(user, command("약사", "대학원 졸업"));
    assertThat(badges.forUser(user)).isEqualTo(new VerificationBadgeService.Badges(true, false));
  }

  @Test void legacyApprovalNeedsExplicitReviewAndMismatchNeverGetsABadge() {
    long user = member("MALE");
    long document = submit(user, "EMPLOYMENT");
    db.update("update user_verification_document set review_status='APPROVED',subject_value=null where id=?", document);
    assertThat(badges.forUser(user).employmentVerified()).isFalse();
    reviews.review(document, false, "APPROVED", null, null);
    assertThat(badges.forUser(user).employmentVerified()).isTrue();
    db.update("update user_profile set job='다른 직업' where user_id=?", user);
    assertThat(badges.forUser(user).employmentVerified()).isFalse();
    assertThatThrownBy(() -> reviews.review(document, false, "APPROVED", null, null))
        .isInstanceOf(BusinessException.class);
    assertThat(badges.forUsers(List.of())).isEmpty();
  }

  @Test void matchingAndInterestResponsesUseTheSameCurrentCertification() {
    long a = member("FEMALE"), b = member("MALE");
    long document = submit(b, "EMPLOYMENT");
    reviews.review(document, false, "APPROVED", null, null);
    db.update("insert into match_proposal(match_type,user_a_id,user_b_id,status,is_s_grade_guaranteed,user_a_decision,user_b_decision,matched_at,expires_at,created_at,updated_at) values ('AUTO',?,?,'PENDING',false,'PENDING','PENDING',CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", a, b, java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(86400)));
    long match = db.queryForObject("select max(id) from match_proposal", Long.class);
    assertThat(matches.findMatch(match, a).employmentVerified()).isTrue();
    assertThat(matches.findPendingMatches(a)).anySatisfy(m -> assertThat(m.employmentVerified()).isTrue());
    interests.send(a, b, com.oao.backend.interest.domain.UserInterest.InterestType.EXPRESS, null);
    assertThat(interests.sent(a).getFirst().employmentVerified()).isTrue();
    profiles.updateProfile(b, command("연구원", "대학교 졸업"));
    assertThat(matches.findMatch(match, a).employmentVerified()).isFalse();
    assertThat(interests.sent(a).getFirst().employmentVerified()).isFalse();
  }

  @Test void reorderPreservesPhotoIdentityFilesReviewAndMatchingEligibility() {
    long user = member("FEMALE");
    var before = photos.findIntroPhoto(user);
    var ids = before.photos().stream().map(ProfileIntroPhotoService.ProfilePhotoView::id).toList();
    var reordered = photos.reorderPhotos(user, ids.reversed());
    assertThat(reordered.photos().stream().map(ProfileIntroPhotoService.ProfilePhotoView::id)).containsExactlyElementsOf(ids.reversed());
    assertThat(reordered.photos().stream().map(ProfileIntroPhotoService.ProfilePhotoView::photoUrl))
        .containsExactlyElementsOf(before.photos().reversed().stream().map(ProfileIntroPhotoService.ProfilePhotoView::photoUrl).toList());
    assertThat(reordered.photos()).allSatisfy(p -> assertThat(p.reviewStatus()).isEqualTo("APPROVED"));
    assertThat(reordered.photoUrl()).isEqualTo(before.photos().getLast().photoUrl());
    assertThat(db.queryForObject("select matching_enabled from matching_profile where user_id=?", Boolean.class, user)).isTrue();
    assertThat(db.queryForObject("select approval_status from user_account where id=?", String.class, user)).isEqualTo("APPROVED");
    assertThat(reordered.intro()).isEqualTo(before.intro());
  }

  @Test void staleDuplicateForeignAndIncompleteOrdersFailWithoutPartialChanges() {
    long user = member("MALE"), other = member("FEMALE");
    var before = photos.findIntroPhoto(user);
    var ids = before.photos().stream().map(ProfileIntroPhotoService.ProfilePhotoView::id).toList();
    long foreign = photos.findIntroPhoto(other).photos().getFirst().id();
    for (List<Long> invalid : List.of(List.of(ids.getFirst(), ids.getFirst()), List.of(ids.getFirst(), foreign),
        List.of(ids.getFirst()), Arrays.asList(ids.getFirst(), null), List.of(ids.getFirst(), Long.MAX_VALUE))) {
      assertThatThrownBy(() -> photos.reorderPhotos(user, invalid)).isInstanceOf(BusinessException.class);
      assertThat(photos.findIntroPhoto(user)).isEqualTo(before);
    }
    db.update("insert into profile_photo(user_id,image_url,display_order,review_status,created_at,updated_at) values (?,'/third.jpg',3,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", user);
    assertThatThrownBy(() -> photos.reorderPhotos(user, ids)).isInstanceOf(BusinessException.class);
    var current = photos.findIntroPhoto(user).photos();
    var reordered = photos.reorderPhotos(user, current.stream().map(ProfileIntroPhotoService.ProfilePhotoView::id).toList().reversed());
    assertThat(reordered.photos().getFirst().reviewStatus()).isEqualTo("PENDING");
  }

  @Test void allSixPhotosCanBeReorderedWithoutChangingTheirReviewStates() {
    long user = member("FEMALE");
    for (int order = 3; order <= 6; order++)
      db.update("insert into profile_photo(user_id,image_url,display_order,review_status,created_at,updated_at) values (?,?,?,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", user, "/optional-"+order+".jpg", order);
    var before = photos.findIntroPhoto(user).photos();
    var ids = before.stream().map(ProfileIntroPhotoService.ProfilePhotoView::id).toList().reversed();
    var after = photos.reorderPhotos(user, ids).photos();
    assertThat(after.stream().map(ProfileIntroPhotoService.ProfilePhotoView::id)).containsExactlyElementsOf(ids);
    for (int i = 0; i < 6; i++) {
      assertThat(after.get(i).photoUrl()).isEqualTo(before.get(5-i).photoUrl());
      assertThat(after.get(i).reviewStatus()).isEqualTo(before.get(5-i).reviewStatus());
      assertThat(after.get(i).displayOrder()).isEqualTo(i+1);
    }
  }

  private long submit(long user, String type) {
    reviews.upload(user, type, new MockMultipartFile("file", "fixture.pdf", "application/pdf", "%PDF-1.4 fixture".getBytes()));
    return ((Number) reviews.documents(user).getFirst().get("id")).longValue();
  }

  private UserProfileService.ProfileUpdateCommand command(String job, String education) {
    return new UserProfileService.ProfileUpdateCommand("가상회원", LocalDate.of(1995, 5, 20), UserAccount.Gender.FEMALE,
        job, 175, "AVERAGE", "NON_SMOKER", "SOCIAL", "NONE", "ENFP", education, "서울 강남구", null, List.of("헬스", "영화"));
  }

  private long member(String gender) {
    var user = UserAccount.createPending();
    user.updateBasicInfo("가상회원", LocalDate.of(1995, 5, 20));
    user.updateGender(UserAccount.Gender.valueOf(gender));
    user.approve(UserAccount.MemberGrade.S, null);
    long id = users.saveAndFlush(user).getId();
    db.update("insert into user_profile(user_id,job,education,phone_verified_at,created_at,updated_at) values (?,'약사','대학교 졸업',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id);
    db.update("insert into matching_profile(user_id,job_intro,matching_enabled,created_at,updated_at) values (?,'일상 속 즐거움을 함께 나누고 싶어요.',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id);
    for (int i = 1; i <= 2; i++) db.update("insert into profile_photo(user_id,image_url,display_order,review_status,created_at,updated_at) values (?,?,?,'APPROVED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, "/fixture-"+id+"-"+i+".jpg", i);
    db.update("insert into heart_wallet(user_id,balance,created_at,updated_at) values (?,100,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id);
    return id;
  }
}

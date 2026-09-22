package com.oao.backend.matching.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "matching_profile")
public class MatchingProfile extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;
  private String jobIntro;
  private String datingStyle;
  private String personalityKeywords;

  public String getDatingStyle() {
    return datingStyle;
  }

  public java.util.List<String> getPersonalityKeywords() {
    return personalityKeywords == null || personalityKeywords.isBlank()
        ? java.util.List.of()
        : java.util.Arrays.asList(personalityKeywords.split(","));
  }

  private boolean matchingEnabled;
  private Instant lastAutoMatchedAt;
  private int autoMatchCount;
  private int sGradeGuaranteedMatchCount;
  private int noResponseCount;

  protected MatchingProfile() {}

  public static MatchingProfile create(Long userId) {
    MatchingProfile profile = new MatchingProfile();
    profile.userId = userId;
    profile.matchingEnabled = true;
    profile.autoMatchCount = 0;
    profile.sGradeGuaranteedMatchCount = 0;
    profile.noResponseCount = 0;
    return profile;
  }

  public void pauseMatching() {
    this.matchingEnabled = false;
  }

  public void updateIntro(String intro) {
    this.jobIntro = intro;
  }

  public boolean isEligibleForSGradeGuarantee(int guaranteeCount) {
    return sGradeGuaranteedMatchCount < guaranteeCount;
  }

  public void recordAutoMatch(boolean sGradeGuaranteed) {
    this.autoMatchCount++;
    this.lastAutoMatchedAt = Instant.now();
    if (sGradeGuaranteed) {
      this.sGradeGuaranteedMatchCount++;
    }
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getJobIntro() {
    return jobIntro;
  }

  public boolean isMatchingEnabled() {
    return matchingEnabled;
  }

  public static boolean hasRequiredMatchingDetails(String style, String keywords) {
    if (style == null || style.isBlank() || style.trim().length() > 1000 || keywords == null)
      return false;
    var values = java.util.Arrays.stream(keywords.split(",")).map(String::trim).filter(v -> !v.isBlank()).toList();
    return values.size() == 3 && new java.util.HashSet<>(values).size() == 3
        && values.stream().allMatch(v -> v.length() <= 30);
  }

  public Instant getLastAutoMatchedAt() {
    return lastAutoMatchedAt;
  }

  public int getAutoMatchCount() {
    return autoMatchCount;
  }
}

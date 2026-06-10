package com.oao.backend.user.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.config.UploadProperties;
import com.oao.backend.matching.domain.MatchingProfile;
import com.oao.backend.matching.repository.MatchingProfileRepository;
import com.oao.backend.user.domain.ProfilePhoto;
import com.oao.backend.user.repository.ProfilePhotoRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileIntroPhotoService {

	private static final int MIN_INTRO_LENGTH = 10;
	private static final int MAX_INTRO_LENGTH = 300;
	public static final int MIN_PHOTO_COUNT = 2;
	private static final int MAX_PHOTO_COUNT = 6;
	private static final long MAX_PHOTO_SIZE_BYTES = 5L * 1024L * 1024L;
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final MatchingProfileRepository matchingProfileRepository;
	private final ProfilePhotoRepository profilePhotoRepository;
	private final UploadProperties uploadProperties;

	public ProfileIntroPhotoService(
		MatchingProfileRepository matchingProfileRepository,
		ProfilePhotoRepository profilePhotoRepository,
		UploadProperties uploadProperties
	) {
		this.matchingProfileRepository = matchingProfileRepository;
		this.profilePhotoRepository = profilePhotoRepository;
		this.uploadProperties = uploadProperties;
	}

	@Transactional(readOnly = true)
	public IntroPhotoView findIntroPhoto(Long userId) {
		MatchingProfile matchingProfile = matchingProfileRepository.findByUserId(userId).orElse(null);
		List<ProfilePhoto> photos = profilePhotoRepository.findByUserIdOrderByDisplayOrderAscIdAsc(userId);
		String intro = matchingProfile == null ? null : matchingProfile.getJobIntro();
		String photoUrl = primaryPhotoUrl(photos);
		return new IntroPhotoView(intro, photoUrl, toPhotoViews(photos), isCompleted(intro, photos));
	}

	@Transactional
	public IntroPhotoView saveIntroPhoto(Long userId, String intro, MultipartFile photo) {
		return saveIntroPhoto(userId, intro, null, photo);
	}

	@Transactional
	public IntroPhotoView saveIntroPhoto(Long userId, String intro, List<MultipartFile> photos, MultipartFile legacyPhoto) {
		String normalizedIntro = normalizeIntro(intro);
		List<ProfilePhoto> existingPhotos = profilePhotoRepository.findByUserIdOrderByDisplayOrderAscIdAsc(userId);
		List<MultipartFile> newPhotos = normalizePhotos(photos);
		List<String> oldPhotoUrlsToDelete = new ArrayList<>();

		MatchingProfile matchingProfile = matchingProfileRepository.findByUserId(userId)
			.orElseGet(() -> matchingProfileRepository.save(MatchingProfile.create(userId)));
		matchingProfile.updateIntro(normalizedIntro);

		if (!newPhotos.isEmpty()) {
			validateMinimumPhotoCount(newPhotos.size());
			existingPhotos.stream()
				.map(ProfilePhoto::getImageUrl)
				.forEach(oldPhotoUrlsToDelete::add);
			replacePhotoSet(userId, existingPhotos, newPhotos);
		} else if (hasNewPhoto(legacyPhoto)) {
			if (existingPhotos.size() < MIN_PHOTO_COUNT) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, minimumPhotoMessage());
			}
			replacePrimaryPhoto(userId, existingPhotos, legacyPhoto, oldPhotoUrlsToDelete);
		} else if (existingPhotos.size() < MIN_PHOTO_COUNT) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, minimumPhotoMessage());
		}

		deleteProfilePhotoFiles(oldPhotoUrlsToDelete);
		List<ProfilePhoto> savedPhotos = profilePhotoRepository.findByUserIdOrderByDisplayOrderAscIdAsc(userId);
		String primaryPhotoUrl = primaryPhotoUrl(savedPhotos);
		return new IntroPhotoView(normalizedIntro, primaryPhotoUrl, toPhotoViews(savedPhotos), isCompleted(normalizedIntro, savedPhotos));
	}

	private boolean hasNewPhoto(MultipartFile photo) {
		return photo != null && !photo.isEmpty();
	}

	private List<MultipartFile> normalizePhotos(List<MultipartFile> photos) {
		if (photos == null) {
			return List.of();
		}
		List<MultipartFile> normalizedPhotos = photos.stream()
			.filter(this::hasNewPhoto)
			.toList();
		if (normalizedPhotos.size() > MAX_PHOTO_COUNT) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Profile photos can be up to " + MAX_PHOTO_COUNT + ".");
		}
		return normalizedPhotos;
	}

	private void validateMinimumPhotoCount(int photoCount) {
		if (photoCount < MIN_PHOTO_COUNT) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, minimumPhotoMessage());
		}
	}

	private String minimumPhotoMessage() {
		return "Profile photos must be at least " + MIN_PHOTO_COUNT + ".";
	}

	private void replacePhotoSet(Long userId, List<ProfilePhoto> existingPhotos, List<MultipartFile> newPhotos) {
		List<ProfilePhoto> nextPhotos = new ArrayList<>();
		for (int index = 0; index < newPhotos.size(); index++) {
			nextPhotos.add(ProfilePhoto.create(userId, storePhoto(userId, newPhotos.get(index)), index + 1));
		}

		profilePhotoRepository.deleteAll(existingPhotos);
		profilePhotoRepository.flush();
		profilePhotoRepository.saveAll(nextPhotos);
	}

	private void replacePrimaryPhoto(
		Long userId,
		List<ProfilePhoto> existingPhotos,
		MultipartFile legacyPhoto,
		List<String> oldPhotoUrlsToDelete
	) {
		String imageUrl = storePhoto(userId, legacyPhoto);
		ProfilePhoto existingPrimaryPhoto = existingPhotos.stream()
			.min(Comparator.comparing(ProfilePhoto::getDisplayOrder).thenComparing(ProfilePhoto::getId))
			.orElse(null);

		if (existingPrimaryPhoto == null) {
			profilePhotoRepository.save(ProfilePhoto.create(userId, imageUrl, 1));
			return;
		}

		oldPhotoUrlsToDelete.add(existingPrimaryPhoto.getImageUrl());
		existingPrimaryPhoto.updateImageUrl(imageUrl);
	}

	private String normalizeIntro(String intro) {
		String normalizedIntro = intro == null ? "" : intro.trim();
		if (normalizedIntro.length() < MIN_INTRO_LENGTH || normalizedIntro.length() > MAX_INTRO_LENGTH) {
			throw new BusinessException(
				HttpStatus.BAD_REQUEST,
				"Intro must be between " + MIN_INTRO_LENGTH + " and " + MAX_INTRO_LENGTH + " characters."
			);
		}
		return normalizedIntro;
	}

	private String storePhoto(Long userId, MultipartFile photo) {
		if (photo == null || photo.isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Profile photo is required.");
		}
		if (photo.getSize() > MAX_PHOTO_SIZE_BYTES) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Profile photo must be 5MB or smaller.");
		}

		String contentType = photo.getContentType();
		if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Only jpeg, png, and webp images are allowed.");
		}

		String extension = extension(contentType);
		String fileName = UUID.randomUUID() + "." + extension;
		Path userUploadDir = Path.of(uploadProperties.rootDir(), "profile-photos", String.valueOf(userId))
			.toAbsolutePath()
			.normalize();
		Path uploadPath = userUploadDir.resolve(fileName);

		try {
			Files.createDirectories(userUploadDir);
			photo.transferTo(uploadPath);
		} catch (IOException exception) {
			throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Profile photo could not be saved.");
		}

		return trimTrailingSlash(uploadProperties.publicPath()) + "/profile-photos/" + userId + "/" + fileName;
	}

	private String extension(String contentType) {
		return switch (contentType.toLowerCase(Locale.ROOT)) {
			case "image/png" -> "png";
			case "image/webp" -> "webp";
			default -> "jpg";
		};
	}

	private boolean isCompleted(String intro, List<ProfilePhoto> photos) {
		return intro != null
			&& intro.trim().length() >= MIN_INTRO_LENGTH
			&& photos.stream()
				.filter(photo -> photo.getImageUrl() != null && !photo.getImageUrl().isBlank())
				.count() >= MIN_PHOTO_COUNT;
	}

	private String primaryPhotoUrl(List<ProfilePhoto> photos) {
		if (photos.isEmpty()) {
			return null;
		}
		return photos.get(0).getImageUrl();
	}

	private List<ProfilePhotoView> toPhotoViews(List<ProfilePhoto> photos) {
		return photos.stream()
			.map(photo -> new ProfilePhotoView(photo.getImageUrl(), photo.getDisplayOrder()))
			.toList();
	}

	private void deleteProfilePhotoFiles(List<String> photoUrls) {
		for (String photoUrl : photoUrls) {
			Optional<Path> uploadPath = resolveUploadPath(photoUrl);
			if (uploadPath.isEmpty()) {
				continue;
			}
			try {
				Files.deleteIfExists(uploadPath.get());
			} catch (IOException exception) {
				// File cleanup failure should not block profile completion.
			}
		}
	}

	private Optional<Path> resolveUploadPath(String imageUrl) {
		if (imageUrl == null || imageUrl.isBlank()) {
			return Optional.empty();
		}
		String publicPath = trimTrailingSlash(uploadProperties.publicPath());
		if (!imageUrl.startsWith(publicPath + "/")) {
			return Optional.empty();
		}

		Path uploadRoot = Path.of(uploadProperties.rootDir()).toAbsolutePath().normalize();
		String relativePath = imageUrl.substring(publicPath.length() + 1);
		Path uploadPath = uploadRoot.resolve(relativePath).normalize();
		if (!uploadPath.startsWith(uploadRoot)) {
			return Optional.empty();
		}
		return Optional.of(uploadPath);
	}

	private String trimTrailingSlash(String value) {
		if (value.endsWith("/")) {
			return value.substring(0, value.length() - 1);
		}
		return value;
	}

	public record IntroPhotoView(String intro, String photoUrl, List<ProfilePhotoView> photos, boolean completed) {
	}

	public record ProfilePhotoView(String photoUrl, Integer displayOrder) {
	}
}

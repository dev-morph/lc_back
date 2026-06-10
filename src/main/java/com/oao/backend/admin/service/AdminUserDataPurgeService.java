package com.oao.backend.admin.service;

import com.oao.backend.config.UploadProperties;
import com.oao.backend.common.BusinessException;
import com.oao.backend.user.domain.AdminUser;
import com.oao.backend.user.repository.AdminUserRepository;
import com.oao.backend.user.repository.UserAccountRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserDataPurgeService {

	private final JdbcTemplate jdbcTemplate;
	private final AdminUserRepository adminUserRepository;
	private final UserAccountRepository userAccountRepository;
	private final UploadProperties uploadProperties;

	public AdminUserDataPurgeService(
		JdbcTemplate jdbcTemplate,
		AdminUserRepository adminUserRepository,
		UserAccountRepository userAccountRepository,
		UploadProperties uploadProperties
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.adminUserRepository = adminUserRepository;
		this.userAccountRepository = userAccountRepository;
		this.uploadProperties = uploadProperties;
	}

	@Transactional
	public AdminUserPurgeResult purgeTestUserData(Long targetUserId, AdminUser admin) {
		validateTargetUserExists(targetUserId);
		if (admin != null && targetUserId.equals(admin.getUserId())) {
			throw new BusinessException(HttpStatus.CONFLICT, "Cannot purge yourself.");
		}
		if (adminUserRepository.existsByUserId(targetUserId)) {
			throw new BusinessException(HttpStatus.CONFLICT, "Admin user cannot be purged.");
		}
		return purgeUserData(targetUserId, false);
	}

	@Transactional
	public AdminUserPurgeResult purgeDevSelfTestUserData(Long targetUserId) {
		validateTargetUserExists(targetUserId);
		return purgeUserData(targetUserId, true);
	}

	private void validateTargetUserExists(Long targetUserId) {
		if (targetUserId == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "User id is required.");
		}
		if (!userAccountRepository.existsById(targetUserId)) {
			throw new BusinessException(HttpStatus.NOT_FOUND, "User not found.");
		}
	}

	private AdminUserPurgeResult purgeUserData(Long targetUserId, boolean deleteAdminUser) {
		List<String> photoUrls = queryStrings(
			"select image_url from profile_photo where user_id = ?",
			targetUserId
		);
		List<Long> matchIds = queryLongs(
			"""
				select id
				from match_proposal
				where user_a_id = ? or user_b_id = ? or requested_by_user_id = ?
				""",
			targetUserId,
			targetUserId,
			targetUserId
		);
		List<Long> chatRoomIds = matchIds.isEmpty()
			? List.of()
			: queryLongs(
				"select id from chat_room where match_id in (%s)".formatted(placeholders(matchIds)),
				matchIds.toArray()
			);
		List<Long> premiumRequestIds = queryLongs(
			"select id from premium_intro_request where user_id = ?",
			targetUserId
		);

		Map<String, Integer> deletedRows = new LinkedHashMap<>();
		addCount(deletedRows, "chat_message", deleteIn("chat_message", "chat_room_id", chatRoomIds));
		addCount(deletedRows, "chat_message", jdbcTemplate.update("delete from chat_message where sender_user_id = ?", targetUserId));
		addCount(deletedRows, "chat_room_member_state", deleteIn("chat_room_member_state", "chat_room_id", chatRoomIds));
		addCount(deletedRows, "chat_room_member_state", jdbcTemplate.update("delete from chat_room_member_state where user_id = ?", targetUserId));
		addCount(deletedRows, "chat_room", deleteIn("chat_room", "id", chatRoomIds));
		addCount(deletedRows, "user_interest", jdbcTemplate.update(
			"delete from user_interest where sender_user_id = ? or receiver_user_id = ?",
			targetUserId,
			targetUserId
		));
		addCount(deletedRows, "premium_intro_request_keyword", deleteIn(
			"premium_intro_request_keyword",
			"premium_intro_request_id",
			premiumRequestIds
		));
		addCount(deletedRows, "premium_intro_request", jdbcTemplate.update(
			"delete from premium_intro_request where user_id = ?",
			targetUserId
		));
		addCount(deletedRows, "match_proposal", deleteIn("match_proposal", "id", matchIds));
		addCount(deletedRows, "report", jdbcTemplate.update(
			"delete from report where reporter_user_id = ? or reported_user_id = ?",
			targetUserId,
			targetUserId
		));
		addCount(deletedRows, "block_relation", jdbcTemplate.update(
			"delete from block_relation where blocker_user_id = ? or blocked_user_id = ?",
			targetUserId,
			targetUserId
		));
		addCount(deletedRows, "app_notification", jdbcTemplate.update(
			"delete from app_notification where user_id = ? or actor_user_id = ?",
			targetUserId,
			targetUserId
		));
		addCount(deletedRows, "notification_log", jdbcTemplate.update("delete from notification_log where user_id = ?", targetUserId));
		addCount(deletedRows, "phone_verification_code", jdbcTemplate.update(
			"delete from phone_verification_code where user_id = ?",
			targetUserId
		));
		addCount(deletedRows, "user_terms_agreement", jdbcTemplate.update(
			"delete from user_terms_agreement where user_id = ?",
			targetUserId
		));
		addCount(deletedRows, "user_verification_document", jdbcTemplate.update(
			"delete from user_verification_document where user_id = ?",
			targetUserId
		));
		addCount(deletedRows, "user_grade_history", jdbcTemplate.update("delete from user_grade_history where user_id = ?", targetUserId));
		addCount(deletedRows, "user_personality_keyword", jdbcTemplate.update(
			"delete from user_personality_keyword where user_id = ?",
			targetUserId
		));
		addCount(deletedRows, "user_hobby", jdbcTemplate.update("delete from user_hobby where user_id = ?", targetUserId));
		addCount(deletedRows, "profile_photo", jdbcTemplate.update("delete from profile_photo where user_id = ?", targetUserId));
		addCount(deletedRows, "matching_profile", jdbcTemplate.update("delete from matching_profile where user_id = ?", targetUserId));
		addCount(deletedRows, "user_profile", jdbcTemplate.update("delete from user_profile where user_id = ?", targetUserId));
		addCount(deletedRows, "heart_transaction", jdbcTemplate.update("delete from heart_transaction where user_id = ?", targetUserId));
		addCount(deletedRows, "heart_wallet", jdbcTemplate.update("delete from heart_wallet where user_id = ?", targetUserId));
		addCount(deletedRows, "payment_transaction", jdbcTemplate.update("delete from payment_transaction where user_id = ?", targetUserId));
		addCount(deletedRows, "instant_intro_usage_window", jdbcTemplate.update(
			"delete from instant_intro_usage_window where user_id = ?",
			targetUserId
		));
		addCount(deletedRows, "oauth_account", jdbcTemplate.update("delete from oauth_account where user_id = ?", targetUserId));
		if (deleteAdminUser) {
			addCount(deletedRows, "admin_user", jdbcTemplate.update("delete from admin_user where user_id = ?", targetUserId));
		}
		addCount(deletedRows, "user_account", jdbcTemplate.update("delete from user_account where id = ?", targetUserId));

		FileDeleteSummary fileDeleteSummary = deleteProfilePhotoFiles(photoUrls);
		return new AdminUserPurgeResult(
			targetUserId,
			deletedRows,
			fileDeleteSummary.deletedFileCount(),
			fileDeleteSummary.failedFileCount()
		);
	}

	private List<Long> queryLongs(String sql, Object... args) {
		return jdbcTemplate.queryForList(sql, Long.class, args);
	}

	private List<String> queryStrings(String sql, Object... args) {
		return jdbcTemplate.queryForList(sql, String.class, args);
	}

	private int deleteIn(String table, String column, List<Long> ids) {
		if (ids.isEmpty()) {
			return 0;
		}
		return jdbcTemplate.update(
			"delete from %s where %s in (%s)".formatted(table, column, placeholders(ids)),
			ids.toArray()
		);
	}

	private String placeholders(List<Long> ids) {
		return ids.stream().map(id -> "?").collect(Collectors.joining(", "));
	}

	private void addCount(Map<String, Integer> deletedRows, String tableName, int count) {
		deletedRows.merge(tableName, count, Integer::sum);
	}

	private FileDeleteSummary deleteProfilePhotoFiles(List<String> photoUrls) {
		int deletedFileCount = 0;
		int failedFileCount = 0;
		for (String photoUrl : photoUrls) {
			Optional<Path> uploadPath = resolveUploadPath(photoUrl);
			if (uploadPath.isEmpty()) {
				continue;
			}
			try {
				if (Files.deleteIfExists(uploadPath.get())) {
					deletedFileCount++;
				}
			} catch (IOException exception) {
				failedFileCount++;
			}
		}
		return new FileDeleteSummary(deletedFileCount, failedFileCount);
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
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	public record AdminUserPurgeResult(
		Long userId,
		Map<String, Integer> deletedRows,
		int deletedFileCount,
		int failedFileCount
	) {
	}

	private record FileDeleteSummary(int deletedFileCount, int failedFileCount) {
	}
}

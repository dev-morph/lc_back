package com.oao.backend.user.service;

import com.oao.backend.common.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VerificationReviewService {
  private final DbRows db;
  private final Path privateRoot, publicRoot;
  private final StoredFileCleanup cleanup;
  private final String publicPath;

  public VerificationReviewService(
      DbRows db,
      StoredFileCleanup cleanup,
      @Value("${oao.upload.public-path:/uploads}") String publicPath,
      @Value("${oao.upload.root-dir:uploads}") String root) {
    this.db = db;
    this.cleanup = cleanup;
    this.publicPath = publicPath.replaceAll("/+$", "") + "/";
    publicRoot = Path.of(root).toAbsolutePath().normalize();
    privateRoot = publicRoot.resolveSibling("private-verification");
  }

  public List<Map<String, Object>> documents(Long user) {
    return db.list(
        "select id,user_id,document_type,original_filename,review_status,rejection_reason,created_at from"
            + " user_verification_document where user_id=? order by id desc",
        user);
  }

  public List<Map<String, Object>> queue() {
    return db.list(
        "select"
            + " d.id,d.user_id,d.document_type,d.review_status,d.rejection_reason,d.created_at,u.name"
            + " from user_verification_document d join user_account u on u.id=d.user_id where"
            + " u.status<>'DELETED' order by d.created_at desc limit 200");
  }

  public List<Map<String, Object>> photos() {
    return db.list(
        "select p.*,u.name from profile_photo p join user_account u on u.id=p.user_id where"
            + " u.status<>'DELETED' order by p.created_at desc limit 200");
  }

  public List<Map<String, Object>> myPhotos(Long id) {
    return db.list(
        "select id,image_url,review_status,rejection_reason from profile_photo where user_id=?"
            + " order by display_order",
        id);
  }

  @Transactional
  public void upload(Long user, String type, MultipartFile file) {
    if (!Set.of("IDENTITY", "EMPLOYMENT", "EDUCATION", "OTHER").contains(type))
      throw bad("서류 종류를 선택해주세요.");
    if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
      throw bad("5MB 이하의 JPG, PNG 또는 PDF를 업로드해주세요.");
    if (db.count("select count(*) from user_verification_document where user_id=?", user) >= 20)
      throw bad("서류는 최대 20개까지 보관할 수 있습니다. 기존 서류를 삭제해주세요.");
    Path target = null;
    try {
      byte[] bytes = file.getBytes();
      String ext = extension(bytes);
      Files.createDirectories(privateRoot);
      target = privateRoot.resolve(UUID.randomUUID() + ext);
      Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
      cleanup.removeOnRollback(target);
      db.jdbc.update(
          "insert into"
              + " user_verification_document(user_id,document_type,file_url,original_filename,review_status,created_at,updated_at)"
              + " values (?,?,?,?,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
          user,
          type,
          target.getFileName().toString(),
          safeFilename(file.getOriginalFilename()));
      db.jdbc.update(
          "update user_account set approval_status='PENDING',rejection_reason=null where id=? and"
              + " approval_status='REJECTED'",
          user);
    } catch (IOException e) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "서류를 저장하지 못했습니다.");
    }
  }

  private String extension(byte[] b) {
    if (b.length >= 4 && b[0] == (byte) 0xff && b[1] == (byte) 0xd8 && b[2] == (byte) 0xff)
      return ".jpg";
    if (b.length >= 8 && b[0] == (byte) 0x89 && b[1] == 80 && b[2] == 78 && b[3] == 71)
      return ".png";
    if (b.length >= 5
        && new String(b, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
      return ".pdf";
    throw bad("JPG, PNG 또는 PDF 형식만 지원합니다.");
  }

  private String safeFilename(String filename) {
    if (filename == null) return null;
    String leaf = filename.replace('\\', '/');
    leaf = leaf.substring(leaf.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
    if (leaf.isEmpty()) return null;
    return leaf.substring(0, leaf.offsetByCodePoints(0, Math.min(leaf.codePointCount(0, leaf.length()), 200)));
  }

  public Resource file(Long id, Long user, boolean admin) {
    var row = db.one("select user_id,file_url from user_verification_document where id=?", id);
    if (!admin && ((Number) row.get("userId")).longValue() != user)
      throw new BusinessException(HttpStatus.FORBIDDEN, "본인 서류만 조회할 수 있습니다.");
    Path path = privateRoot.resolve(row.get("fileUrl").toString()).normalize();
    if (!path.startsWith(privateRoot) || !Files.isRegularFile(path))
      throw new BusinessException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다.");
    return new FileSystemResource(path);
  }

  @Transactional
  public void delete(Long user, Long id) {
    var row = db.one("select user_id,file_url from user_verification_document where id=?", id);
    if (((Number) row.get("userId")).longValue() != user)
      throw new BusinessException(HttpStatus.FORBIDDEN, "본인 서류만 삭제할 수 있습니다.");
    removeFile(privateRoot, row.get("fileUrl").toString());
    db.jdbc.update("delete from user_verification_document where id=?", id);
  }

  @Transactional
  public void review(Long id, boolean photo, String status, String reason, Long admin) {
    if (!Set.of("APPROVED", "REJECTED").contains(status)) throw bad("심사 상태를 선택해주세요.");
    if (status.equals("REJECTED") && (reason == null || reason.isBlank()))
      throw bad("반려 사유를 입력해주세요.");
    String table = photo ? "profile_photo" : "user_verification_document";
    var item = db.one("select user_id from " + table + " where id=?", id);
    db.jdbc.update(
        "update "
            + table
            + " set review_status=?,rejection_reason=?,updated_at=CURRENT_TIMESTAMP where id=?",
        status,
        status.equals("REJECTED") ? reason : null,
        id);
    if (!photo)
      db.jdbc.update(
          "update user_verification_document set"
              + " reviewed_by_admin_id=?,reviewed_at=CURRENT_TIMESTAMP where id=?",
          admin,
          id);
    if (status.equals("REJECTED")) {
      db.jdbc.update(
          "update matching_profile set matching_enabled=false where user_id=?", item.get("userId"));
      db.jdbc.update(
          "update user_account set approval_status='REJECTED',rejection_reason=? where id=?",
          reason,
          item.get("userId"));
    }
  }

  @Transactional
  public void deleteUserFiles(Long id) {
    for (var row : db.list("select file_url from user_verification_document where user_id=?", id))
      removeFile(privateRoot, row.get("fileUrl").toString());
    for (var row : db.list("select image_url from profile_photo where user_id=?", id)) {
      String url = row.get("imageUrl").toString();
      if (url.startsWith(publicPath)) removeFile(publicRoot, url.substring(publicPath.length()));
    }
    db.jdbc.update("delete from user_verification_document where user_id=?", id);
    db.jdbc.update("delete from profile_photo where user_id=?", id);
  }

  private void removeFile(Path root, String name) {
    Path p = root.resolve(name).normalize();
    if (p.startsWith(root)) cleanup.enqueue(p);
  }

  private BusinessException bad(String m) {
    return new BusinessException(HttpStatus.BAD_REQUEST, m);
  }
}

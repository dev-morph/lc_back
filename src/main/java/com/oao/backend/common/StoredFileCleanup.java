package com.oao.backend.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** A persisted deletion queue keeps database rollbacks and filesystem changes consistent. */
@Service
public class StoredFileCleanup {
  private final DbRows db;
  private final Path publicRoot, privateRoot;

  public StoredFileCleanup(DbRows db, @Value("${oao.upload.root-dir:uploads}") String root) {
    this.db = db;
    publicRoot = Path.of(root).toAbsolutePath().normalize();
    privateRoot = publicRoot.resolveSibling("private-verification");
  }

  public void enqueue(Path path) {
    Path normalized = path.toAbsolutePath().normalize();
    if (!allowed(normalized)) throw new IllegalArgumentException("Invalid cleanup path");
    db.jdbc.update(
        "insert into stored_file_cleanup(file_path,created_at,next_attempt_at) values"
            + " (?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        normalized.toString());
  }

  public void removeOnRollback(Path path) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) return;
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK)
              try {
                Files.deleteIfExists(path);
              } catch (IOException e) {
                org.slf4j.LoggerFactory.getLogger(StoredFileCleanup.class)
                    .warn("Rollback file cleanup failed", e);
              }
          }
        });
  }

  @Scheduled(fixedDelay = 30000, initialDelay = 30000)
  @Transactional
  public void drain() {
    for (var row :
        db.list(
            "select * from stored_file_cleanup where next_attempt_at<=CURRENT_TIMESTAMP order by id"
                + " limit 50 for update")) {
      Path path = Path.of(row.get("filePath").toString()).normalize();
      try {
        if (!allowed(path)) throw new IOException("File is outside upload roots");
        Files.deleteIfExists(path);
        db.jdbc.update("delete from stored_file_cleanup where id=?", row.get("id"));
      } catch (IOException e) {
        db.jdbc.update(
            "update stored_file_cleanup set attempts=attempts+1,last_error=?,next_attempt_at=?"
                + " where id=?",
            e.getClass().getSimpleName(),
            Timestamp.from(Instant.now().plusSeconds(300)),
            row.get("id"));
      }
    }
  }

  private boolean allowed(Path p) {
    return !p.equals(publicRoot)
        && !p.equals(privateRoot)
        && (p.startsWith(publicRoot) || p.startsWith(privateRoot));
  }
}

package com.oao.backend.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class AfterCommit {
  private AfterCommit() {}

  public static void run(Runnable work) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              work.run();
            }
          });
    else work.run();
  }
}

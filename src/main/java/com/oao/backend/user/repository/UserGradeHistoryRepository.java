package com.oao.backend.user.repository;

import com.oao.backend.user.domain.UserGradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGradeHistoryRepository extends JpaRepository<UserGradeHistory, Long> {
}

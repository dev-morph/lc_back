package com.oao.backend.user.repository;

import com.oao.backend.user.domain.UserHobby;
import com.oao.backend.user.domain.UserHobby.UserHobbyId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserHobbyRepository extends JpaRepository<UserHobby, UserHobbyId> {

	List<UserHobby> findByIdUserId(Long userId);

	void deleteByIdUserId(Long userId);
}

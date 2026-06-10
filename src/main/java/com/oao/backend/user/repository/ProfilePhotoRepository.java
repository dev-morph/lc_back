package com.oao.backend.user.repository;

import com.oao.backend.user.domain.ProfilePhoto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfilePhotoRepository extends JpaRepository<ProfilePhoto, Long> {

	Optional<ProfilePhoto> findFirstByUserIdOrderByDisplayOrderAscIdAsc(Long userId);

	List<ProfilePhoto> findByUserIdOrderByDisplayOrderAscIdAsc(Long userId);
}

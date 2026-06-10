package com.oao.backend.user.repository;

import com.oao.backend.user.domain.Hobby;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HobbyRepository extends JpaRepository<Hobby, Long> {

	List<Hobby> findByNameIn(Collection<String> names);
}

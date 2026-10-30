package org.example.backendweride.platform.profile.infrastructure.persistence.jpa.repositories;

import org.example.backendweride.platform.profile.infrastructure.persistence.jpa.ProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProfileRepository extends JpaRepository<ProfileEntity, Long> {
    Optional<ProfileEntity> findByUserId(String userId);

    boolean existsByUserId(String userId);
}

package org.example.backendweride.platform.profile.application.internal.queryservices;

import org.example.backendweride.platform.profile.domain.model.aggregates.Profile;
import org.example.backendweride.platform.profile.domain.model.commands.CreateProfileCommand;
import org.example.backendweride.platform.profile.domain.model.queries.*;
import org.example.backendweride.platform.profile.domain.services.ProfileQueryService;
import org.example.backendweride.platform.profile.infrastructure.persistence.jpa.ProfileEntity;
import org.example.backendweride.platform.profile.infrastructure.persistence.jpa.repositories.ProfileRepository;
import org.example.backendweride.platform.profile.infrastructure.privacy.UserDataGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ProfileQueryServiceImpl implements ProfileQueryService {
    private final ProfileRepository profiles;
    private final UserDataGuard guard;

    public ProfileQueryServiceImpl(ProfileRepository profiles, UserDataGuard guard) {
        this.profiles = profiles;
        this.guard = guard;
    }

    public Optional<Profile> handle(GetProfileByIdQuery query) {
        return profiles.findById(query.profileId()).map(ProfileEntity::toDomain);
    }

    @Transactional
    public Optional<Profile> handle(GetProfileByUserIdQuery query) {
        guard.assertWritable(query.userId());
        return Optional.of(
                profiles.findByUserId(query.userId())
                        .orElseGet(
                                () ->
                                        profiles.saveAndFlush(
                                                new ProfileEntity(
                                                        new Profile(
                                                                new CreateProfileCommand(
                                                                        query.userId())))))
                        .toDomain());
    }
}

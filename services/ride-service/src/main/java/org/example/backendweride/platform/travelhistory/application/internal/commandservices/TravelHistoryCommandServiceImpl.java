package org.example.backendweride.platform.travelhistory.application.internal.commandservices;

import org.example.backendweride.platform.ride.infrastructure.privacy.UserDataGuard;
import org.example.backendweride.platform.travelhistory.domain.model.aggregates.TravelHistory;
import org.example.backendweride.platform.travelhistory.domain.model.commands.*;
import org.example.backendweride.platform.travelhistory.domain.services.commandservices.TravelHistoryCommandService;
import org.example.backendweride.platform.travelhistory.infrastructure.persistence.jpa.TravelHistoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@Transactional
public class TravelHistoryCommandServiceImpl implements TravelHistoryCommandService {
    private final TravelHistoryRepository history;
    private final UserDataGuard guard;

    public TravelHistoryCommandServiceImpl(TravelHistoryRepository history, UserDataGuard guard) {
        this.history = history;
        this.guard = guard;
    }

    public Optional<TravelHistory> handle(CreateTravelHistoryCommand c) {
        guard.assertWritable(c.userId());
        return Optional.of(history.save(new TravelHistory(c)));
    }

    public Optional<TravelHistory> handle(UpdateTravelHistoryCommand c) {
        guard.assertWritable(c.userId());
        return history.findById(c.id())
                .map(
                        t -> {
                            if (!c.userId().equals(t.getUserId()))
                                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                            t.updateFromCommand(c);
                            return history.save(t);
                        });
    }
}

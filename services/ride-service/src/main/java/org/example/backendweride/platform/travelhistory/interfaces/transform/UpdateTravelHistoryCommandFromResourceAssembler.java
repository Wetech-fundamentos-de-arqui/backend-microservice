package org.example.backendweride.platform.travelhistory.interfaces.transform;

import org.example.backendweride.platform.travelhistory.domain.model.commands.UpdateTravelHistoryCommand;
import org.example.backendweride.platform.travelhistory.interfaces.resources.UpdateTravelHistoryResource;

public class UpdateTravelHistoryCommandFromResourceAssembler {
    public static UpdateTravelHistoryCommand toCommandFromResource(
            Long id, UpdateTravelHistoryResource r) {
        return new UpdateTravelHistoryCommand(
                id,
                r.userId(),
                r.location(),
                r.vehicle(),
                r.image(),
                r.tripDuration(),
                r.travelDistance());
    }
}

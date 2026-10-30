package org.example.backendweride.platform.travelhistory.domain.model.commands;

public record CreateTravelHistoryCommand(
        String userId,
        String location,
        String vehicle,
        String image,
        String tripDuration,
        String travelDistance) {}

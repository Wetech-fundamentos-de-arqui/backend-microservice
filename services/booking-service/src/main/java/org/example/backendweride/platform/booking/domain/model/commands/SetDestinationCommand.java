package org.example.backendweride.platform.booking.domain.model.commands;

public record SetDestinationCommand(String bookingId, Double lat, Double lng, String address) {}

package org.example.backendweride.platform.shared.security;

/**
 * Principal built from the JWT claims (sub + preferred_username), so services never query the accounts table.
 */
public record AuthenticatedUser(String id, String username) {}

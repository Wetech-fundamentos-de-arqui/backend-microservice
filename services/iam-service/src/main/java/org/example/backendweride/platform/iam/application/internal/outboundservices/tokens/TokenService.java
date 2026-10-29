package org.example.backendweride.platform.iam.application.internal.outboundservices.tokens;

import org.example.backendweride.platform.iam.domain.model.aggregates.Account;

/**
 * Token Service Interface
 *
 * @summary Opens a session for the account and issues the bearer token that carries it.
 */
public interface TokenService {
    String generateToken(Account account);
}

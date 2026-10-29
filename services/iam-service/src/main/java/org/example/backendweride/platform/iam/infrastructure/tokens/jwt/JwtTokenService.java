package org.example.backendweride.platform.iam.infrastructure.tokens.jwt;

import org.example.backendweride.platform.iam.application.internal.outboundservices.tokens.TokenService;
import org.example.backendweride.platform.iam.domain.model.aggregates.Account;
import org.example.backendweride.platform.iam.domain.model.aggregates.AccountSession;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Issues RS256 tokens: sub = account id, preferred_username, sid, iss, aud, iat, exp.
 */
@Service
public class JwtTokenService implements TokenService {
    private final AccountSessionRepository sessions;
    private final JwtEncoder encoder;
    private final String issuer;
    private final List<String> audiences;
    private final int expirationDays;

    public JwtTokenService(AccountSessionRepository sessions, JwtEncoder encoder,
                           OAuth2ResourceServerProperties properties,
                           @Value("${authorization.jwt.expiration.days}") int expirationDays) {
        this.sessions = sessions;
        this.encoder = encoder;
        this.issuer = properties.getJwt().getIssuerUri();
        this.audiences = properties.getJwt().getAudiences();
        this.expirationDays = expirationDays;
    }

    @Override
    @Transactional
    public String generateToken(Account account) {
        var issuedAt = Instant.now();
        var expiration = issuedAt.plus(expirationDays, ChronoUnit.DAYS);
        var attributes = RequestContextHolder.getRequestAttributes();
        var userAgent = attributes instanceof ServletRequestAttributes request ? request.getRequest().getHeader("User-Agent") : null;
        var session = sessions.save(new AccountSession(account.getId(), expiration, userAgent));
        var claims = JwtClaimsSet.builder()
                .subject(String.valueOf(account.getId()))
                .claim("preferred_username", account.getUserName())
                .claim("sid", session.getId())
                .issuer(issuer)
                .audience(audiences)
                .issuedAt(issuedAt)
                .expiresAt(expiration)
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}

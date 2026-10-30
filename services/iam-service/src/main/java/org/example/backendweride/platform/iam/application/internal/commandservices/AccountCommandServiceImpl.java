package org.example.backendweride.platform.iam.application.internal.commandservices;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.example.backendweride.platform.iam.application.internal.outboundservices.hashing.HashingService;
import org.example.backendweride.platform.iam.application.internal.outboundservices.tokens.TokenService;
import org.example.backendweride.platform.iam.domain.model.aggregates.Account;
import org.example.backendweride.platform.iam.domain.model.commands.SignInCommand;
import org.example.backendweride.platform.iam.domain.model.commands.SignUpCommand;
import org.example.backendweride.platform.iam.domain.services.AccountCommandService;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class AccountCommandServiceImpl implements AccountCommandService {
    private final AccountRepository accountRepository;
    private final HashingService hashingService;
    private final TokenService tokenService;

    public AccountCommandServiceImpl(AccountRepository accountRepository, HashingService hashingService, TokenService tokenService) {
        this.accountRepository = accountRepository;
        this.hashingService = hashingService;
        this.tokenService = tokenService;
    }

    @Override
    public Optional<Account> handle(SignUpCommand command) {
        // Check lengths here: Bean Validation's default warning includes rejected field values.
        if (command.username() == null || command.username().isBlank() || command.username().length() > 254
                || command.password() == null || command.password().isBlank()
                || command.password().length() < 8 || command.password().length() > 64
                || command.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CREDENTIALS_FORMAT");
        if(accountRepository.existsByUserName(command.username()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists");

        var account = new Account(command, hashingService.encode(command.password()));

        return Optional.of(accountRepository.save(account));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Optional<ImmutablePair<Account, String>> handle(SignInCommand command) {
        if (command.username() == null || command.username().isBlank() || command.username().length() > 254
                || command.password() == null || command.password().isBlank() || command.password().length() > 72
                || command.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CREDENTIALS_FORMAT");
        var account = accountRepository.findLockedByUserName(command.username())
                .filter(a -> a.isActive() && hashingService.matches(command.password(), a.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        var token = tokenService.generateToken(account);
        return Optional.of(ImmutablePair.of(account, token));
    }
}

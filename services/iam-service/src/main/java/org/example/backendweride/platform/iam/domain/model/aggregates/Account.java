package org.example.backendweride.platform.iam.domain.model.aggregates;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.backendweride.platform.iam.domain.model.commands.SignUpCommand;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Account Aggregate Root
 *
 * @summary Represents an account in WeRide Platform.
 */
@Entity
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Account extends AbstractAggregateRoot<Account> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @NotBlank
    @Getter
    @Size(max=254)
    @Column(unique = true)
    private String userName;

    @NotBlank
    @Getter
    private String password;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    public Account(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        this.userName = username;
        this.password = password;
    }

    public Account(SignUpCommand command, String hashedPassword) {
        if (command == null) {
            throw new IllegalArgumentException("SignUpCommand cannot be null");
        }
        if (command.username() == null || command.username().isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (hashedPassword == null || hashedPassword.isBlank()) {
            throw new IllegalArgumentException("Hashed password cannot be null or empty");
        }
        this.userName = command.username();
        this.password = hashedPassword;
    }

    public boolean isActive() { return status == AccountStatus.ACTIVE; }
    public void markDeletionPending() { this.status = AccountStatus.DELETION_PENDING; }
    public void reactivate() { this.status = AccountStatus.ACTIVE; }

    public Account updatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        this.password = password;
        return this;
    }

    public Account updateUserName(String userName) {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        this.userName = userName;
        return this;
    }

}

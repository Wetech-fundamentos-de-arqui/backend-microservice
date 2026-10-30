package org.example.backendweride.platform.profile.domain.model.aggregates;

import lombok.Getter;

import org.example.backendweride.platform.profile.domain.model.commands.CreateProfileCommand;
import org.example.backendweride.platform.profile.domain.model.commands.UpdateProfileCommand;
import org.example.backendweride.platform.profile.domain.model.valueobjects.Preferences;
import org.example.backendweride.platform.profile.domain.model.valueobjects.Statistics;

import java.time.LocalDate;

@Getter
public class Profile {

    private Long id;
    private final String userId;
    private String name;
    private String phone;
    private String profilePicture;
    private LocalDate dateOfBirth;
    private String address;
    private String emergencyContact;
    private final Preferences preferences;
    private final Statistics statistics;

    public Profile(CreateProfileCommand command) {
        this.userId = command.userId();
        this.name = "";
        this.phone = "";
        this.profilePicture = "";
        this.dateOfBirth = null;
        this.address = "";
        this.emergencyContact = "";
        this.preferences = Preferences.defaultPreferences();
        this.statistics = Statistics.defaultStatistics();
    }

    public Profile(
            Long id,
            String userId,
            String name,
            String phone,
            String profilePicture,
            LocalDate dateOfBirth,
            String address,
            String emergencyContact,
            Preferences preferences,
            Statistics statistics) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.phone = phone;
        this.profilePicture = profilePicture;
        this.dateOfBirth = dateOfBirth;
        this.address = address;
        this.emergencyContact = emergencyContact;
        this.preferences = preferences;
        this.statistics = statistics;
    }

    public void update(UpdateProfileCommand command) {
        if (command.name() != null) this.name = command.name();
        if (command.phone() != null) this.phone = command.phone();
        if (command.profilePicture() != null) this.profilePicture = command.profilePicture();
        if (command.dateOfBirth() != null) this.dateOfBirth = command.dateOfBirth();
        if (command.address() != null) this.address = command.address();
        if (command.emergencyContact() != null) this.emergencyContact = command.emergencyContact();
    }

    public void setId(Long id) {
        this.id = id;
    }
}

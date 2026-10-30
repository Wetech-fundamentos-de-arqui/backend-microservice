package org.example.backendweride.platform.trip.domain.aggregates;

import jakarta.persistence.*;

import lombok.Getter;

import org.example.backendweride.platform.trip.domain.commands.CreateTripCommand;
import org.example.backendweride.platform.trip.domain.valueobjects.RouteCoordinates;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter
    @Column(unique = true)
    private String bookingId;

    @Getter private String userId;

    @Getter private String vehicleId;

    @Getter private String startLocationId;

    @Getter private String endLocationId;

    @Getter private String route;

    @ElementCollection(fetch = FetchType.EAGER)
    @OrderColumn(name = "point_index")
    @Getter
    private List<RouteCoordinates> routeCoordinates = new ArrayList<>();

    @Getter private Date startDate;
    @Getter private Date endDate;
    @Getter private int duration;
    @Getter private double distance;
    @Getter private double averageSpeed;
    @Getter private double maxSpeed;
    @Getter private double totalCost;
    @Getter private double carbonSaved;
    @Getter private int caloriesBurned;
    @Getter private String weather;
    @Getter private int temperature;
    @Getter private String status;

    @Getter
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @Column(length = 1000)
    private List<String> incidentReports = new ArrayList<>();

    @Getter
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @Column(length = 1000)
    private List<String> photos = new ArrayList<>();

    protected Trip() {}

    public Trip(CreateTripCommand tripCommand) {
        this.userId = tripCommand.userId();
        this.bookingId = tripCommand.bookingId();
        this.vehicleId = tripCommand.vehicleId();
        this.startLocationId = tripCommand.startLocationId();
        this.endLocationId = tripCommand.endLocationId();
        this.route = tripCommand.route();
        this.routeCoordinates = tripCommand.routeCoordinates() == null ? new ArrayList<>() : new ArrayList<>(tripCommand.routeCoordinates());
        this.startDate = tripCommand.startDate();
        this.endDate = tripCommand.endDate();
        this.duration = tripCommand.duration();
        this.distance = tripCommand.distance();
        this.averageSpeed = tripCommand.averageSpeed();
        this.maxSpeed = tripCommand.maxSpeed();
        this.totalCost = tripCommand.totalCost();
        this.carbonSaved = tripCommand.carbonSaved();
        this.caloriesBurned = tripCommand.caloriesBurned();
        this.weather = tripCommand.weather();
        this.temperature = tripCommand.temperature();
        this.status = tripCommand.status();
        this.incidentReports = tripCommand.incidentReports() == null ? new ArrayList<>() : new ArrayList<>(tripCommand.incidentReports());
        this.photos = tripCommand.photos() == null ? new ArrayList<>() : new ArrayList<>(tripCommand.photos());
    }

    @Getter
    @Column(length = 64)
    private String completionHash;

    @Getter private String routeSource = "gps";

    public Trip(
            String bookingId,
            String userId,
            String vehicleId,
            String startLocationId,
            String endLocationId,
            Date startedAt) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.vehicleId = vehicleId;
        this.startLocationId = startLocationId;
        this.endLocationId = endLocationId;
        this.startDate = startedAt;
        this.status = "in_progress";
        this.route = "";
    }

    public void complete(
            double cost,
            double distance,
            int duration,
            double speed,
            List<RouteCoordinates> coordinates,
            String source,
            String hash) {
        this.totalCost = cost;
        this.distance = distance;
        this.duration = duration;
        this.averageSpeed = speed;
        this.routeCoordinates.clear();
        if (coordinates != null) this.routeCoordinates.addAll(coordinates);
        this.routeSource = source == null ? "gps" : source;
        this.endDate = new Date();
        this.status = "completed";
        this.completionHash = hash;
    }

    public void cancel() {
        if (!"completed".equals(status)) {
            status = "cancelled";
            endDate = new Date();
        }
    }
}

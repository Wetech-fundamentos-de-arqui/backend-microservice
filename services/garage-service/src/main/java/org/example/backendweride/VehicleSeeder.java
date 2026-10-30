package org.example.backendweride;

import org.example.backendweride.platform.garage.domain.model.aggregates.Vehicle;
import org.example.backendweride.platform.garage.domain.model.commands.CreateVehicleCommand;
import org.example.backendweride.platform.garage.infrastructure.persistence.jpa.VehicleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class VehicleSeeder implements CommandLineRunner {

    private final VehicleRepository vehicleRepository;

    @Value("${app.seed.demo:true}")
    private boolean seedDemoData;

    public VehicleSeeder(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Demo vehicles/plans only load when app.seed.demo=true (default). Production
        // must set app.seed.demo=false so no demo data reaches real environments.
        if (!seedDemoData) return;

        if (vehicleRepository.count() == 0) {
            try {
                vehicleRepository.saveAll(
                        List.of(
                                vehicle(
                                        "Xiaomi",
                                        "M365",
                                        2023,
                                        82,
                                        25,
                                        30,
                                        12.5,
                                        "black",
                                        "XM001",
                                        loc(0),
                                        "available",
                                        "electric_scooter",
                                        "1",
                                        0.5,
                                        "assets/vehicles/xiaomi-m365.jpg",
                                        List.of("GPS", "Bluetooth", "Mobile App", "LED Lights"),
                                        "good",
                                        "2024-09-15T10:00:00Z",
                                        "2024-11-15T10:00:00Z",
                                        1250.5,
                                        4.6),
                                vehicle(
                                        "Trek",
                                        "FX 3",
                                        2022,
                                        0,
                                        0,
                                        0,
                                        11.8,
                                        "blue",
                                        "TK002",
                                        loc(1),
                                        "reserved",
                                        "bike",
                                        "1",
                                        0.3,
                                        "assets/vehicles/trek-fx3.jpg",
                                        List.of("Lightweight", "21 Gears", "Quick Release"),
                                        "excellent",
                                        "2024-09-20T14:00:00Z",
                                        "2024-12-20T14:00:00Z",
                                        456.2,
                                        4.8),
                                vehicle(
                                        "Segway",
                                        "Ninebot Max",
                                        2023,
                                        65,
                                        30,
                                        40,
                                        18.7,
                                        "gray",
                                        "SG003",
                                        loc(2),
                                        "maintenance",
                                        "electric_scooter",
                                        "1",
                                        0.6,
                                        "assets/vehicles/segway-max.jpg",
                                        List.of("GPS", "Bluetooth", "Anti-theft", "Cruise Control"),
                                        "under_repair",
                                        "2025-10-05T08:00:00Z",
                                        "2025-12-05T08:00:00Z",
                                        892.3,
                                        4.7),
                                vehicle(
                                        "Giant",
                                        "Escape 3",
                                        2023,
                                        0,
                                        0,
                                        0,
                                        12.9,
                                        "red",
                                        "GT004",
                                        loc(3),
                                        "available",
                                        "bike",
                                        "2",
                                        0.35,
                                        "assets/vehicles/giant-escape3.jpg",
                                        List.of("Aluminum Frame", "24 Gears", "Disc Brakes"),
                                        "good",
                                        "2025-09-10T10:00:00Z",
                                        "2025-12-10T10:00:00Z",
                                        678.5,
                                        4.5),
                                vehicle(
                                        "Ninebot",
                                        "ES2",
                                        2022,
                                        90,
                                        25,
                                        25,
                                        12.5,
                                        "black",
                                        "NB005",
                                        loc(4),
                                        "available",
                                        "electric_scooter",
                                        "2",
                                        0.45,
                                        "assets/vehicles/ninebot-es2.jpg",
                                        List.of("LED Display", "App Control", "Shock Absorption"),
                                        "excellent",
                                        "2025-09-25T14:00:00Z",
                                        "2025-11-25T14:00:00Z",
                                        523.8,
                                        4.9),
                                vehicle(
                                        "Specialized",
                                        "Sirrus X",
                                        2023,
                                        0,
                                        0,
                                        0,
                                        11.5,
                                        "green",
                                        "SP006",
                                        loc(5),
                                        "reserved",
                                        "bike",
                                        "2",
                                        0.4,
                                        "assets/vehicles/specialized-sirrus.jpg",
                                        List.of(
                                                "Carbon Fork",
                                                "Hydraulic Brakes",
                                                "Tubeless Ready"),
                                        "good",
                                        "2025-09-18T11:00:00Z",
                                        "2025-12-18T11:00:00Z",
                                        345.2,
                                        4.8),
                                vehicle(
                                        "Xiaomi",
                                        "Pro 2",
                                        2023,
                                        75,
                                        25,
                                        45,
                                        14.2,
                                        "black",
                                        "XM007",
                                        loc(6),
                                        "available",
                                        "electric_scooter",
                                        "1",
                                        0.55,
                                        "assets/vehicles/xiaomi-pro2.jpg",
                                        List.of("GPS", "E-ABS", "Dual Brakes", "IPX4 Waterproof"),
                                        "good",
                                        "2025-09-22T09:00:00Z",
                                        "2025-11-22T09:00:00Z",
                                        1045.7,
                                        4.7),
                                vehicle(
                                        "Segway",
                                        "E22",
                                        2023,
                                        88,
                                        20,
                                        22,
                                        13.5,
                                        "white",
                                        "SG008",
                                        loc(7),
                                        "available",
                                        "electric_scooter",
                                        "1",
                                        0.5,
                                        "assets/vehicles/segway-e22.jpg",
                                        List.of("Folding Design", "LED Lights", "App Control"),
                                        "excellent",
                                        "2025-09-28T10:00:00Z",
                                        "2025-11-28T10:00:00Z",
                                        234.9,
                                        4.6),
                                vehicle(
                                        "Razor",
                                        "E300",
                                        2022,
                                        70,
                                        24,
                                        20,
                                        21.0,
                                        "blue",
                                        "RZ009",
                                        loc(8),
                                        "available",
                                        "electric_scooter",
                                        "3",
                                        0.4,
                                        "assets/vehicles/razor-e300.jpg",
                                        List.of("Wide Deck", "Pneumatic Tires", "Rear Suspension"),
                                        "good",
                                        "2025-09-15T13:00:00Z",
                                        "2025-11-15T13:00:00Z",
                                        567.3,
                                        4.4),
                                vehicle(
                                        "Unagi",
                                        "Model One",
                                        2023,
                                        95,
                                        32,
                                        25,
                                        10.9,
                                        "orange",
                                        "UN010",
                                        loc(9),
                                        "available",
                                        "electric_scooter",
                                        "3",
                                        0.65,
                                        "assets/vehicles/unagi-one.jpg",
                                        List.of(
                                                "Dual Motor",
                                                "Lightweight",
                                                "Premium Design",
                                                "Smart Display"),
                                        "excellent",
                                        "2025-10-01T08:00:00Z",
                                        "2025-12-01T08:00:00Z",
                                        123.4,
                                        4.9)));
            } catch (DataAccessException e) {
                throw new IllegalStateException(
                        "VehicleSeeder no pudo insertar los vehicles. Revisa el esquema de la base"
                            + " de datos.",
                        e);
            }
        }
    }

    private static String loc(int index) {
        return String.valueOf(index + 1);
    }

    private static Vehicle vehicle(
            String brand,
            String model,
            int year,
            int battery,
            int maxSpeed,
            int range,
            double weight,
            String color,
            String licensePlate,
            String location,
            String status,
            String type,
            String companyId,
            double pricePerMinute,
            String image,
            List<String> features,
            String maintenanceStatus,
            String lastMaintenance,
            String nextMaintenance,
            double totalKilometers,
            double rating) {
        return new Vehicle(
                new CreateVehicleCommand(
                        brand,
                        model,
                        year,
                        battery,
                        maxSpeed,
                        range,
                        weight,
                        color,
                        licensePlate,
                        location,
                        status,
                        type,
                        companyId,
                        pricePerMinute,
                        image,
                        features,
                        maintenanceStatus,
                        date(lastMaintenance),
                        date(nextMaintenance),
                        totalKilometers,
                        rating));
    }

    private static Date date(String value) {
        return Date.from(Instant.parse(value));
    }
}

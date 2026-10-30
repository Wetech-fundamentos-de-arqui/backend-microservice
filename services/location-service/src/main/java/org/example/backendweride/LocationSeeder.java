package org.example.backendweride;

import org.example.backendweride.platform.location.domain.commands.CreateLocationCommand;
import org.example.backendweride.platform.location.domain.model.aggregates.Location;
import org.example.backendweride.platform.location.domain.valueobjects.Coordinates;
import org.example.backendweride.platform.location.domain.valueobjects.OperatingHours;
import org.example.backendweride.platform.location.infrastructure.persistence.jpa.LocationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class LocationSeeder implements CommandLineRunner {

    private final LocationRepository locationRepository;

    @Value("${app.seed.demo:true}")
    private boolean seedDemoData;

    public LocationSeeder(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Demo vehicles/plans only load when app.seed.demo=true (default). Production
        // must set app.seed.demo=false so no demo data reaches real environments.
        if (!seedDemoData) return;

        // Los vehicles demo apuntan a estas locations por su id real: sin ellas el mapa no puede
        // ubicarlos.
        List<Location> locations = locationRepository.findAll();
        if (locations.isEmpty()) {
            try {
                locations =
                        locationRepository.saveAll(
                                List.of(
                                        location(
                                                "Plaza Mayor",
                                                "Plaza Mayor, Lima Centro",
                                                -12.046374,
                                                -77.042793,
                                                15,
                                                8,
                                                "05:00",
                                                "23:00",
                                                List.of("WiFi", "Security", "Shelter"),
                                                "Lima",
                                                "Estación principal en el corazón histórico de"
                                                    + " Lima",
                                                "assets/locations/plaza-mayor.jpg"),
                                        location(
                                                "Parque Kennedy",
                                                "Av. Larco, Miraflores",
                                                -12.1205,
                                                -77.029833,
                                                20,
                                                12,
                                                "06:00",
                                                "22:00",
                                                List.of("WiFi", "Security", "Charging", "Shelter"),
                                                "Miraflores",
                                                "Estación en el corazón de Miraflores",
                                                "assets/locations/parque-kennedy.jpg"),
                                        location(
                                                "Larcomar",
                                                "Malecón de la Reserva, Miraflores",
                                                -12.131944,
                                                -77.030278,
                                                18,
                                                10,
                                                "07:00",
                                                "23:00",
                                                List.of("WiFi", "Security", "Charging"),
                                                "Miraflores",
                                                "Estación con vista al mar",
                                                "assets/locations/larcomar.jpg"),
                                        location(
                                                "Jockey Plaza",
                                                "Av. Javier Prado Este, Surco",
                                                -12.084334,
                                                -76.977074,
                                                25,
                                                15,
                                                "06:00",
                                                "23:30",
                                                List.of(
                                                        "WiFi",
                                                        "Security",
                                                        "Charging",
                                                        "Shelter",
                                                        "Lockers"),
                                                "Surco",
                                                "Estación en centro comercial principal",
                                                "assets/locations/jockey-plaza.jpg"),
                                        location(
                                                "San Isidro Golf",
                                                "Av. Camino Real, San Isidro",
                                                -12.097778,
                                                -77.037222,
                                                16,
                                                9,
                                                "06:00",
                                                "22:00",
                                                List.of("WiFi", "Security", "Shelter"),
                                                "San Isidro",
                                                "Estación en zona empresarial",
                                                "assets/locations/san-isidro-golf.jpg"),
                                        location(
                                                "Real Plaza Salaverry",
                                                "Av. Salaverry, Jesús María",
                                                -12.081111,
                                                -77.047222,
                                                22,
                                                14,
                                                "06:30",
                                                "23:00",
                                                List.of("WiFi", "Security", "Charging", "Shelter"),
                                                "Jesús María",
                                                "Estación en centro comercial",
                                                "assets/locations/real-plaza-salaverry.jpg"),
                                        location(
                                                "Barranco Central",
                                                "Av. Grau, Barranco",
                                                -12.146667,
                                                -77.016944,
                                                14,
                                                7,
                                                "07:00",
                                                "00:00",
                                                List.of("WiFi", "Security"),
                                                "Barranco",
                                                "Estación en distrito bohemio",
                                                "assets/locations/barranco-central.jpg"),
                                        location(
                                                "Óvalo Gutiérrez",
                                                "Av. Santa Cruz, Miraflores",
                                                -12.119444,
                                                -77.027778,
                                                20,
                                                11,
                                                "06:00",
                                                "22:30",
                                                List.of("WiFi", "Security", "Charging", "Shelter"),
                                                "Miraflores",
                                                "Estación en zona comercial",
                                                "assets/locations/ovalo-gutierrez.jpg"),
                                        location(
                                                "Pueblo Libre Centro",
                                                "Av. Brasil, Pueblo Libre",
                                                -12.075278,
                                                -77.063889,
                                                12,
                                                6,
                                                "06:30",
                                                "22:00",
                                                List.of("WiFi", "Security", "Shelter"),
                                                "Pueblo Libre",
                                                "Estación en zona residencial",
                                                "assets/locations/pueblo-libre.jpg"),
                                        location(
                                                "Universidad del Pacífico",
                                                "Av. Salaverry, Jesús María",
                                                -12.082222,
                                                -77.045278,
                                                18,
                                                13,
                                                "05:30",
                                                "22:30",
                                                List.of(
                                                        "WiFi",
                                                        "Security",
                                                        "Charging",
                                                        "Shelter",
                                                        "Student Discount"),
                                                "Jesús María",
                                                "Estación universitaria",
                                                "assets/locations/universidad-pacifico.jpg")));
            } catch (DataAccessException e) {
                throw new IllegalStateException(
                        "LocationSeeder no pudo insertar las locations. Revisa el esquema de la"
                            + " base de datos.",
                        e);
            }
        }
    }

    private static Location location(
            String name,
            String address,
            double lat,
            double lng,
            int capacity,
            int availableSpots,
            String open,
            String close,
            List<String> amenities,
            String district,
            String description,
            String image) {
        return new Location(
                new CreateLocationCommand(
                        name,
                        address,
                        new Coordinates(lat, lng),
                        "station",
                        capacity,
                        availableSpots,
                        true,
                        new OperatingHours(open, close),
                        amenities,
                        district,
                        description,
                        image));
    }
}

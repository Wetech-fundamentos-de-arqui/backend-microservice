package org.example.backendweride;

import org.example.backendweride.platform.plan.domain.commands.CreatePlanCommand;
import org.example.backendweride.platform.plan.domain.model.aggregates.Plan;
import org.example.backendweride.platform.plan.infrastructure.persistence.jpa.PlanRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class PlanSeeder implements CommandLineRunner {

    private final PlanRepository planRepository;

    @Value("${app.seed.demo:true}")
    private boolean seedDemoData;

    public PlanSeeder(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Demo vehicles/plans only load when app.seed.demo=true (default). Production
        // must set app.seed.demo=false so no demo data reaches real environments.
        if (!seedDemoData) return;

        if (planRepository.count() == 0) {
            try {
                planRepository.saveAll(
                        List.of(
                                plan(
                                        "Plan Normal",
                                        "Ideal para usuarios ocasionales que buscan una opcion"
                                            + " economica",
                                        3.99f,
                                        0.6f,
                                        5,
                                        60,
                                        30,
                                        10,
                                        List.of(
                                                "Acceso a scooters estandar",
                                                "10% de descuento en cada viaje",
                                                "Soporte al cliente basico",
                                                "30 minutos gratis al mes"),
                                        "#3B82F6",
                                        false),
                                plan(
                                        "Plan Estudiantil",
                                        "Perfecto para estudiantes que necesitan movilidad"
                                            + " frecuente",
                                        5.99f,
                                        0.4f,
                                        10,
                                        90,
                                        60,
                                        20,
                                        List.of(
                                                "Acceso a scooters premium",
                                                "20% de descuento en cada viaje",
                                                "Soporte al cliente prioritario",
                                                "Viajes ilimitados los fines de semana",
                                                "60 minutos gratis al mes"),
                                        "#10B981",
                                        true),
                                plan(
                                        "Plan Empresarial",
                                        "Solucion completa para profesionales y empresas",
                                        9.99f,
                                        0.3f,
                                        20,
                                        120,
                                        120,
                                        30,
                                        List.of(
                                                "Acceso a todos los vehiculos",
                                                "30% de descuento en cada viaje",
                                                "Soporte prioritario 24/7",
                                                "Viajes ilimitados",
                                                "120 minutos gratis al mes",
                                                "Reportes mensuales",
                                                "Facturacion centralizada"),
                                        "#8B5CF6",
                                        false)));
            } catch (DataAccessException e) {
                throw new IllegalStateException(
                        "PlanSeeder no pudo insertar los planes. Revisa el esquema de la base de"
                            + " datos.",
                        e);
            }
        }
    }

    private static Plan plan(
            String name,
            String description,
            float price,
            float pricePerMinute,
            int maxTripsPerDay,
            int maxMinutesPerTrip,
            int freeMinutesPerMonth,
            int discountPercentage,
            List<String> benefits,
            String color,
            boolean popular) {
        return new Plan(
                new CreatePlanCommand(
                        name,
                        description,
                        price,
                        "USD",
                        pricePerMinute,
                        "monthly",
                        30,
                        maxTripsPerDay,
                        maxMinutesPerTrip,
                        freeMinutesPerMonth,
                        discountPercentage,
                        benefits,
                        color,
                        popular,
                        true));
    }
}

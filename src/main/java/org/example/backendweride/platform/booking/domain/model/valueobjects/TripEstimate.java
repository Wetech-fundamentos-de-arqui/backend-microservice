package org.example.backendweride.platform.booking.domain.model.valueobjects;

/**
 * Estimated trip from the pickup station to a free destination point.
 * It is only an estimate shown before riding; the final charge is still time based.
 */
public record TripEstimate(double distanceKm, int durationMinutes, double pricePerMinute, double estimatedCost) {

    /** Destinations farther than this from the pickup station are out of coverage. */
    public static final double MAX_DISTANCE_KM = 60;
    /** Streets are not straight lines: straight distance is stretched by this factor. */
    public static final double ROUTE_FACTOR = 1.3;
    public static final double SCOOTER_KMH = 15;
    public static final double BIKE_KMH = 12;
    /** Fixed charge for every destination change once the ride has started. */
    public static final double DESTINATION_CHANGE_FEE = 1.0;

    public static TripEstimate between(double fromLat, double fromLng, double toLat, double toLng,
                                       String vehicleType, double pricePerMinute) {
        double distance = straightKm(fromLat, fromLng, toLat, toLng) * ROUTE_FACTOR;
        double speed = "bike".equals(vehicleType) ? BIKE_KMH : SCOOTER_KMH;
        int minutes = Math.max(1, (int) Math.ceil(distance / speed * 60));
        return new TripEstimate(round2(distance), minutes, pricePerMinute, round2(minutes * pricePerMinute));
    }

    /** Haversine distance in kilometres. */
    public static double straightKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}

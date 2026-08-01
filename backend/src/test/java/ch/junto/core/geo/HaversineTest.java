package ch.junto.core.geo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HaversineTest {

    @Test
    void zurichToBern() {
        double distance = Haversine.distanceKm(47.3769, 8.5417, 46.9480, 7.4474);
        assertThat(distance).isBetween(95.0, 97.0);
    }

    @Test
    void samePoint() {
        double distance = Haversine.distanceKm(47.3769, 8.5417, 47.3769, 8.5417);
        assertThat(distance).isEqualTo(0.0);
    }

    @Test
    void antipodalPoints() {
        double distance = Haversine.distanceKm(0.0, 0.0, 0.0, 180.0);
        assertThat(distance).isBetween(20010.0, 20020.0);
    }
}

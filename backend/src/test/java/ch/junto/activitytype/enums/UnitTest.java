package ch.junto.activitytype.enums;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UnitTest {

    @Test
    void hasAllExpectedValues() {
        Unit[] values = Unit.values();
        assertThat(values).containsExactlyInAnyOrder(
            Unit.KM, Unit.MILES, Unit.METERS,
            Unit.MINUTES, Unit.HOURS,
            Unit.LAPS, Unit.SETS, Unit.REPS,
            Unit.KG, Unit.LBS
        );
    }

    @Test
    void displayLabels() {
        assertThat(Unit.KM.getLabel()).isEqualTo("km");
        assertThat(Unit.MILES.getLabel()).isEqualTo("miles");
        assertThat(Unit.METERS.getLabel()).isEqualTo("m");
        assertThat(Unit.MINUTES.getLabel()).isEqualTo("minutes");
        assertThat(Unit.HOURS.getLabel()).isEqualTo("hours");
        assertThat(Unit.LAPS.getLabel()).isEqualTo("laps");
        assertThat(Unit.SETS.getLabel()).isEqualTo("sets");
        assertThat(Unit.REPS.getLabel()).isEqualTo("reps");
        assertThat(Unit.KG.getLabel()).isEqualTo("kg");
        assertThat(Unit.LBS.getLabel()).isEqualTo("lbs");
    }
}

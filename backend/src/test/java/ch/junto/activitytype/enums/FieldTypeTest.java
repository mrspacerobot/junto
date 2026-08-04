package ch.junto.activitytype.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FieldTypeTest {

  @Test
  void hasAllExpectedValues() {
    FieldType[] values = FieldType.values();
    assertThat(values)
        .containsExactlyInAnyOrder(
            FieldType.TEXT,
            FieldType.NUMBER,
            FieldType.NUMBER_WITH_UNIT,
            FieldType.SELECT,
            FieldType.BOOLEAN,
            FieldType.TIME_OF_DAY,
            FieldType.DATE_RANGE);
  }
}

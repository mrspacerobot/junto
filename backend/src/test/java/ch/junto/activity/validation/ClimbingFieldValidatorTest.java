package ch.junto.activity.validation;

import ch.junto.activity.entity.Activity;
import ch.junto.activity.entity.CustomFields;
import ch.junto.activitytype.model.FieldType;
import ch.junto.core.exception.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClimbingFieldValidatorTest {
    private final ClimbingFieldValidator validator = new ClimbingFieldValidator();

    @Test
    void normalize_populatesSubtypeAndDifficulty() {
        Activity activity = new Activity();
        CustomFields fields = new CustomFields();
        fields.put("style", "Sport");
        fields.put("grade", "5.10a");
        fields.put("environment", "Outdoor");
        fields.put("experienceLevel", "Intermediate");
        activity.setCustomFields(fields);
        validator.normalize(activity);
        assertThat(activity.getSubtype()).isEqualTo("Sport");
        assertThat(activity.getDifficultyLabel()).isEqualTo("5.10a");
    }

    @Test
    void validate_validFields_passes() {
        CustomFields fields = new CustomFields();
        fields.put("style", "Bouldering");
        fields.put("grade", "V5");
        fields.put("environment", "Indoor");
        fields.put("experienceLevel", "Beginner");
        fields.put("needsEquipment", true);
        assertThatCode(() -> validator.validate(fields)).doesNotThrowAnyException();
    }

    @Test
    void validate_missingFields_throwsException() {
        assertThatThrownBy(() -> validator.validate(new CustomFields())).isInstanceOf(ApiException.class);
    }

    @Test
    void validate_invalidStyle_throwsException() {
        CustomFields fields = new CustomFields();
        fields.put("style", "InvalidStyle");
        fields.put("environment", "Outdoor");
        fields.put("experienceLevel", "Intermediate");
        assertThatThrownBy(() -> validator.validate(fields)).isInstanceOf(ApiException.class);
    }

    @Test
    void getFieldDefinitions_returnsCorrectTypes() {
        var defs = validator.getFieldDefinitions();
        assertThat(defs).hasSize(5);
        assertThat(defs.get(0).type()).isEqualTo(FieldType.SELECT);
        assertThat(defs.get(1).type()).isEqualTo(FieldType.TEXT);
        assertThat(defs.get(4).type()).isEqualTo(FieldType.BOOLEAN);
    }
}

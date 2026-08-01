package ch.junto.activitytype;

import ch.junto.activitytype.definition.ActivityTypeDefinition;
import ch.junto.activitytype.definition.ClimbingType;
import ch.junto.activitytype.definition.CyclingType;
import ch.junto.activitytype.definition.CustomFieldDefinition;
import ch.junto.activitytype.definition.RunningType;
import ch.junto.activitytype.definition.SkiingType;
import ch.junto.activitytype.definition.YogaType;
import ch.junto.activitytype.enums.FieldType;
import ch.junto.core.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class ActivityTypeRegistryTest {

    private ActivityTypeDefinition testType(String slug) {
        return testType(slug, List.of("field1"), List.of("field1"));
    }

    private ActivityTypeDefinition testType(String slug, List<String> fieldNames, List<String> summaryFields) {
        List<CustomFieldDefinition> defs = fieldNames.stream()
            .map(n -> new CustomFieldDefinition(n, n, FieldType.TEXT, false, null, null))
            .toList();
        return new ActivityTypeDefinition() {
            @Override public String getSlug() { return slug; }
            @Override public String getName() { return slug; }
            @Override public String getIconName() { return slug; }
            @Override public String getDefaultImageUrl() { return "https://example.com/" + slug + ".jpg"; }
            @Override public List<CustomFieldDefinition> getFieldDefinitions() { return defs; }
            @Override public List<String> getSummaryFields() { return summaryFields; }
        };
    }

    @Test
    void get_knownSlug_returnsDefinition() {
        var registry = new ActivityTypeRegistry(List.of(testType("test")));
        assertThat(registry.get("test").getSlug()).isEqualTo("test");
    }

    @Test
    void get_unknownSlug_throwsNotFoundException() {
        var registry = new ActivityTypeRegistry(List.of(testType("test")));
        assertThatThrownBy(() -> registry.get("unknown"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void exists_knownSlug_returnsTrue() {
        var registry = new ActivityTypeRegistry(List.of(testType("test")));
        assertThat(registry.exists("test")).isTrue();
    }

    @Test
    void exists_unknownSlug_returnsFalse() {
        var registry = new ActivityTypeRegistry(List.of(testType("test")));
        assertThat(registry.exists("unknown")).isFalse();
    }

    @Test
    void listAll_returnsSortedBySlug() {
        var registry = new ActivityTypeRegistry(List.of(
            testType("cycling"), testType("alpine"), testType("boxing")
        ));
        assertThat(registry.listAll())
            .extracting(ActivityTypeDefinition::getSlug)
            .containsExactly("alpine", "boxing", "cycling");
    }

    @Test
    void rejects_duplicateSlugs() {
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(
            testType("test"), testType("test")
        ))).isInstanceOf(IllegalStateException.class)
           .hasMessageContaining("Duplicate");
    }

    @Test
    void rejects_blankSlug() {
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(testType(""))))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejects_invalidSlugFormat() {
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(testType("Bad Slug!"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must match");
    }

    @Test
    void rejects_summaryFieldNotInDefinitions() {
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(
            testType("test", List.of("field1"), List.of("nonexistent"))
        ))).isInstanceOf(IllegalStateException.class)
           .hasMessageContaining("does not reference");
    }

    @Test
    void rejects_duplicateSummaryFields() {
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(
            testType("test", List.of("field1"), List.of("field1", "field1"))
        ))).isInstanceOf(IllegalStateException.class)
           .hasMessageContaining("Duplicate summary");
    }

    @Test
    void rejects_duplicateFieldNames() {
        var badType = new ActivityTypeDefinition() {
            @Override public String getSlug() { return "test"; }
            @Override public String getName() { return "Test"; }
            @Override public String getIconName() { return "test"; }
            @Override public String getDefaultImageUrl() { return "https://example.com/test.jpg"; }
            @Override public List<CustomFieldDefinition> getFieldDefinitions() {
                return List.of(
                    new CustomFieldDefinition("dup", "Dup1", FieldType.TEXT, false, null, null),
                    new CustomFieldDefinition("dup", "Dup2", FieldType.TEXT, false, null, null)
                );
            }
            @Override public List<String> getSummaryFields() { return List.of(); }
        };
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(badType)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate field name");
    }

    @Test
    void rejects_emptyFieldDefinitions() {
        var badType = new ActivityTypeDefinition() {
            @Override public String getSlug() { return "test"; }
            @Override public String getName() { return "Test"; }
            @Override public String getIconName() { return "test"; }
            @Override public String getDefaultImageUrl() { return "https://example.com/test.jpg"; }
            @Override public List<CustomFieldDefinition> getFieldDefinitions() { return List.of(); }
            @Override public List<String> getSummaryFields() { return List.of(); }
        };
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(badType)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least one field");
    }

    private ActivityTypeRegistry fullRegistry() {
        return new ActivityTypeRegistry(List.of(
            new ClimbingType(), new CyclingType(), new RunningType(),
            new SkiingType(), new YogaType()
        ));
    }

    @Test
    void fullRegistry_containsAllFiveTypes() {
        var registry = fullRegistry();
        assertThat(registry.listAll()).hasSize(5);
        assertThat(registry.exists("climbing")).isTrue();
        assertThat(registry.exists("cycling")).isTrue();
        assertThat(registry.exists("running")).isTrue();
        assertThat(registry.exists("skiing")).isTrue();
        assertThat(registry.exists("yoga")).isTrue();
    }

    @Test
    void fullRegistry_listAllIsSortedBySlug() {
        var registry = fullRegistry();
        assertThat(registry.listAll())
            .extracting(ActivityTypeDefinition::getSlug)
            .containsExactly("climbing", "cycling", "running", "skiing", "yoga");
    }

    @Test
    void allTypes_summaryFieldsReferenceActualFieldNames() {
        var registry = fullRegistry();
        for (var type : registry.listAll()) {
            Set<String> fieldNames = type.getFieldDefinitions().stream()
                .map(CustomFieldDefinition::name)
                .collect(Collectors.toSet());
            for (String sf : type.getSummaryFields()) {
                assertThat(fieldNames)
                    .as("Summary field '%s' in %s", sf, type.getSlug())
                    .contains(sf);
            }
        }
    }

    @Test void climbingType_hasCorrectFields() {
        var t = new ClimbingType();
        assertThat(t.getFieldDefinitions()).hasSize(5);
        assertThat(t.getSummaryFields()).containsExactly("style", "grade");
    }

    @Test void cyclingType_hasCorrectFields() {
        var t = new CyclingType();
        assertThat(t.getFieldDefinitions()).hasSize(3);
        assertThat(t.getSummaryFields()).containsExactly("distance");
    }

    @Test void runningType_hasCorrectFields() {
        var t = new RunningType();
        assertThat(t.getFieldDefinitions()).hasSize(3);
        assertThat(t.getSummaryFields()).containsExactly("distance");
    }

    @Test void skiingType_hasCorrectFields() {
        var t = new SkiingType();
        assertThat(t.getFieldDefinitions()).hasSize(2);
        assertThat(t.getSummaryFields()).containsExactly("level");
    }

    @Test void yogaType_hasCorrectFields() {
        var t = new YogaType();
        assertThat(t.getFieldDefinitions()).hasSize(3);
        assertThat(t.getSummaryFields()).containsExactly("style");
    }
}

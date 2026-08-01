package ch.junto.core.config;

import ch.junto.activity.entity.CustomFields;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.locationtech.jts.geom.Point;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.beans.Introspector;

@Configuration
public class OpenApiConfig {

    static {
        // Map JTS Point to a GeoJSON-like schema in OpenAPI
        ObjectSchema pointSchema = new ObjectSchema();
        pointSchema.addProperty("type", new StringSchema()._default("Point"));
        pointSchema.addProperty("coordinates", new ArraySchema()
                .items(new NumberSchema())
                .description("Longitude and Latitude")
                .example(new java.util.ArrayList<Double>() {{
                    add(8.5417);
                    add(47.3769);
                }}));

        SpringDocUtils.getConfig().replaceWithSchema(Point.class, pointSchema);
        SpringDocUtils.getConfig().replaceWithSchema(
            CustomFields.class,
            new MapSchema().additionalProperties(new Schema<>())
        );
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components());
    }

    /**
     * Controller-qualified operation IDs remain stable when endpoint ordering
     * changes and avoid generated names such as create1/list4.
     */
    @Bean
    public OperationCustomizer stableOperationIds() {
        return (operation, handlerMethod) -> {
            String controller = handlerMethod.getBeanType().getSimpleName()
                .replaceFirst("Controller$", "");
            operation.setOperationId(
                Introspector.decapitalize(controller) + capitalize(handlerMethod.getMethod().getName()));
            return operation;
        };
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}

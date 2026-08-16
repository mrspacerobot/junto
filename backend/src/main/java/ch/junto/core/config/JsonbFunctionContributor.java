package ch.junto.core.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registers the PostgreSQL @> containment operator as a named Hibernate function.
 *
 * <p>Usage in Criteria API: cb.isTrue(cb.function("jsonb_contains_op", Boolean.class,
 * root.get("customFields"), cb.literal(json)))
 *
 * <p>Equivalent SQL: custom_fields @> '<json>'::jsonb
 */
public class JsonbFunctionContributor implements FunctionContributor {

  @Override
  public void contributeFunctions(FunctionContributions functionContributions) {
    functionContributions
        .getFunctionRegistry()
        .registerPattern(
            "jsonb_contains_op",
            "(?1 @> ?2::jsonb)",
            functionContributions
                .getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN));
  }
}

package ch.junto.user;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = {})
@Email
@NotBlank
@Pattern(regexp = ValidEmailAddress.PATTERN)
@Size(max = ValidEmailAddress.MAX_LENGTH)
@Target({
  ElementType.FIELD,
  ElementType.METHOD,
  ElementType.PARAMETER,
  ElementType.ANNOTATION_TYPE,
  ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmailAddress {

  /**
   * Browser-compatible email syntax, kept deliberately narrower than RFC 5322 so the server never
   * rejects an address that an HTML {@code <input type="email">} accepts, and never accepts one the
   * browser would block before submission.
   *
   * <p>Exposed as a constant because springdoc/swagger-core reads only Bean Validation annotations
   * that are directly present on a property and does not traverse this composed annotation's
   * meta-annotations. Request DTOs therefore restate the rule as explicit {@code @Schema} metadata
   * built from these constants, so the generated OpenAPI contract in {@code docs/openapi.json}
   * stays in sync with the constraint actually enforced at runtime.
   */
  String PATTERN =
      "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9]"
          + "(?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
          + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$";

  /** Matches the {@code users.email} column width. */
  int MAX_LENGTH = 255;

  String message() default "must be a valid email address";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}

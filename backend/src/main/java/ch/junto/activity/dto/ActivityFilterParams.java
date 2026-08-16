package ch.junto.activity.dto;

import ch.junto.activity.enums.ActivityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Filter parameters for GET /api/activities. Must be a mutable class (not a record) so Spring MVC
 * can bind customFields[key]=value query params into the Map field.
 */
public class ActivityFilterParams {

  private String city;

  @Schema(example = "cycling")
  private String typeSlug;

  private ActivityStatus status;
  private OffsetDateTime fromDate;
  private OffsetDateTime toDate;
  private Boolean joinable;

  /**
   * Custom field equality filters. Requires typeSlug to be set. Only SELECT fields are supported in
   * v1. Example: customFields[style]=Bouldering
   */
  private Map<String, String> customFields;

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getTypeSlug() {
    return typeSlug;
  }

  public void setTypeSlug(String typeSlug) {
    this.typeSlug = typeSlug;
  }

  public ActivityStatus getStatus() {
    return status;
  }

  public void setStatus(ActivityStatus status) {
    this.status = status;
  }

  public OffsetDateTime getFromDate() {
    return fromDate;
  }

  public void setFromDate(OffsetDateTime fromDate) {
    this.fromDate = fromDate;
  }

  public OffsetDateTime getToDate() {
    return toDate;
  }

  public void setToDate(OffsetDateTime toDate) {
    this.toDate = toDate;
  }

  public Boolean getJoinable() {
    return joinable;
  }

  public void setJoinable(Boolean joinable) {
    this.joinable = joinable;
  }

  public Map<String, String> getCustomFields() {
    return customFields;
  }

  public void setCustomFields(Map<String, String> customFields) {
    this.customFields = customFields;
  }

  private Double lat;
  private Double lng;
  private Double radiusKm;

  public Double getLat() {
    return lat;
  }

  public void setLat(Double lat) {
    this.lat = lat;
  }

  public Double getLng() {
    return lng;
  }

  public void setLng(Double lng) {
    this.lng = lng;
  }

  public Double getRadiusKm() {
    return radiusKm;
  }

  public void setRadiusKm(Double radiusKm) {
    this.radiusKm = radiusKm;
  }

  public boolean hasLocation() {
    return lat != null && lng != null;
  }
}

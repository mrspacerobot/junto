package ch.junto.activitytype.enums;

public enum Unit {
  KM("km"),
  MILES("miles"),
  METERS("m"),
  MINUTES("minutes"),
  HOURS("hours"),
  LAPS("laps"),
  SETS("sets"),
  REPS("reps"),
  KG("kg"),
  LBS("lbs");

  private final String label;

  Unit(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}

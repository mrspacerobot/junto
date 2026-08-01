package ch.junto.activity.dto;

public record ActivitySummaryField(
    String fieldName,
    String label,
    Object value,
    String displayValue
) {}

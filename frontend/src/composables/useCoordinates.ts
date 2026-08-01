import { computed, type ComputedRef } from 'vue';

/**
 * GeoJSON Point structure from the API
 */
export interface GeoPoint {
  type: 'Point';
  coordinates: number[]; // [longitude, latitude]
}

/**
 * Composable to work with GeoJSON points in a way that's
 * friendly to UI inputs and map libraries.
 */
export function useCoordinates(point: ComputedRef<GeoPoint | undefined>) {
  const lng = computed(() => point.value?.coordinates[0]);
  const lat = computed(() => point.value?.coordinates[1]);

  const hasCoordinates = computed(() =>
    point.value?.coordinates && point.value.coordinates.length >= 2
  );

  return {
    lat,
    lng,
    hasCoordinates,
    // Helper to format for display
    formatted: computed(() => {
      if (!hasCoordinates.value) return 'Location not set';
      return `${lat.value?.toFixed(4)}, ${lng.value?.toFixed(4)}`;
    })
  };
}

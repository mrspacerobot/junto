<template>
  <div class="w-full h-full rounded-xl overflow-hidden relative">
    <div v-if="hasCoords" ref="mapContainer" class="w-full h-full"></div>
    <div
      v-else
      class="w-full h-full flex items-center justify-center"
      style="
        background: linear-gradient(135deg, #e8f0e0 0%, #d4e4c8 30%, #c2d6b4 60%, #dde8d4 100%);
      "
    >
      <p class="text-primary-600/60 text-sm font-medium">No activities with location</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { iconMap, DEFAULT_ICON } from '@/generated/icon-map';

/** Default map center: Zürich, Switzerland. Override via center prop. */
const ZURICH_CENTER: [number, number] = [47.3769, 8.5417];

interface MapActivity {
  id: string;
  title: string;
  city?: string;
  startsAt: string;
  locationLat?: number;
  locationLng?: number;
  currentParticipants?: number;
  activityTypeIconName?: string;
}

const props = withDefaults(
  defineProps<{
    activities?: MapActivity[];
    center?: [number, number];
  }>(),
  {
    activities: () => [],
  },
);

const router = useRouter();
const mapContainer = ref<HTMLDivElement>();
let map: L.Map | null = null;
let markerLayer: L.LayerGroup | null = null;

const hasValidCoordinates = (activity: MapActivity) =>
  activity.locationLat != null &&
  activity.locationLng != null &&
  activity.locationLat >= -90 &&
  activity.locationLat <= 90 &&
  activity.locationLng >= -180 &&
  activity.locationLng <= 180;

const activitiesWithCoords = computed(() => props.activities.filter(hasValidCoordinates));
const hasCoords = computed(() => activitiesWithCoords.value.length > 0);
const markerVersion = computed(() =>
  activitiesWithCoords.value
    .map(
      (activity) =>
        `${activity.id}:${activity.locationLat}:${activity.locationLng}:${activity.title}`,
    )
    .join('|'),
);

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function escapeHtmlAttr(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function createMarkerIcon(iconName?: string): L.DivIcon {
  const inner = iconName ? iconMap[iconName.toLowerCase()] || DEFAULT_ICON : DEFAULT_ICON;

  return L.divIcon({
    className: 'custom-marker',
    html: `<div style="width:30px;height:30px;border-radius:50%;background:#3d5a1e;display:flex;align-items:center;justify-content:center;border:2px solid white;box-shadow:0 2px 8px rgba(0,0,0,0.2);cursor:pointer;">
      <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>
    </div>`,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
    popupAnchor: [0, -18],
  });
}

function createPopupContent(activity: MapActivity): string {
  const title = escapeHtml(activity.title || 'Untitled');
  const city = escapeHtml(activity.city || 'TBD');
  const id = escapeHtmlAttr(activity.id || '');
  const date = activity.startsAt
    ? new Date(activity.startsAt).toLocaleDateString(undefined, {
        weekday: 'short',
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '';

  return `<div class="map-popup" data-activity-id="${id}" style="font-family:'DM Sans',sans-serif;cursor:pointer;">
    <div style="height:50px;background:linear-gradient(135deg,#d4e4c8,#c2d6b4);border-radius:10px 10px 0 0;display:flex;align-items:flex-end;padding:6px 10px;">
      <span style="font-size:10px;color:#3d5a1e;font-weight:600;">${escapeHtml(date)}</span>
    </div>
    <div style="padding:8px 10px 10px;">
      <div style="font-size:13px;font-weight:700;color:#1a1a1a;line-height:1.3;">${title}</div>
      <div style="font-size:11px;color:#6A6560;margin-top:2px;">${activity.currentParticipants || 0} joined · ${city}</div>
    </div>
  </div>`;
}

function initMap() {
  if (!mapContainer.value) return;

  const center = props.center || ZURICH_CENTER;

  map = L.map(mapContainer.value, {
    center,
    zoom: 12,
    zoomControl: false,
    attributionControl: true,
  });

  L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
    attribution:
      '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/">CARTO</a>',
    subdomains: 'abcd',
    maxZoom: 19,
  }).addTo(map);

  L.control.zoom({ position: 'topright' }).addTo(map);
  map.attributionControl.setPrefix('');

  markerLayer = L.layerGroup().addTo(map);

  // Navigate to activity detail when popup content is clicked.
  // Scoped to each popup's DOM element via Leaflet's popupopen/popupremove events.
  map.on('popupopen', (e: L.PopupEvent) => {
    const popup = e.popup;
    const popupEl = popup.getElement();
    if (!popupEl) return;

    const clickHandler = (ev: Event) => {
      const target = (ev.target as HTMLElement).closest('.map-popup');
      if (!target) return;
      const activityId = (target as HTMLElement).getAttribute('data-activity-id');
      if (activityId) {
        router.push({ name: 'activity-detail', params: { id: activityId } });
      }
    };

    popupEl.addEventListener('click', clickHandler);
    popup.once('remove', () => {
      popupEl.removeEventListener('click', clickHandler);
    });
  });

  updateMarkers();
}

function updateMarkers() {
  if (!map || !markerLayer) return;

  markerLayer.clearLayers();

  if (activitiesWithCoords.value.length === 0) return;

  const bounds = L.latLngBounds([]);

  for (const activity of activitiesWithCoords.value) {
    const latlng = L.latLng(activity.locationLat!, activity.locationLng!);
    bounds.extend(latlng);

    const marker = L.marker(latlng, {
      icon: createMarkerIcon(activity.activityTypeIconName),
    });

    marker.bindPopup(createPopupContent(activity), {
      className: 'custom-popup',
      closeButton: false,
      minWidth: 200,
      maxWidth: 240,
    });

    markerLayer!.addLayer(marker);
  }

  map.fitBounds(bounds, { padding: [30, 30], maxZoom: 14 });
}

onMounted(() => {
  if (hasCoords.value) {
    nextTick(() => initMap());
  }
});

// Re-init map when coords appear/disappear (e.g. after first data fetch)
watch(hasCoords, (val) => {
  if (val && !map) {
    nextTick(() => initMap());
  } else if (!val && map) {
    map.remove();
    map = null;
    markerLayer = null;
  }
});

// Track coordinate-bearing content, including in-place pagination appends.
watch(markerVersion, () => {
  nextTick(() => updateMarkers());
});

onBeforeUnmount(() => {
  map?.remove();
  map = null;
  markerLayer = null;
});
</script>

<style>
.custom-marker {
  background: none !important;
  border: none !important;
}

.custom-popup .leaflet-popup-content-wrapper {
  border-radius: 12px;
  padding: 0;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  border: none;
}

.custom-popup .leaflet-popup-content {
  margin: 0;
  line-height: 1;
}

.custom-popup .leaflet-popup-tip {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.leaflet-control-zoom {
  border: 1px solid #d0cdc6 !important;
  border-radius: 6px !important;
  overflow: hidden;
  box-shadow: none !important;
}

.leaflet-control-zoom a {
  background-color: white !important;
  color: #6a6560 !important;
  width: 28px !important;
  height: 28px !important;
  line-height: 28px !important;
  font-size: 16px !important;
  font-weight: 300 !important;
  border-color: #d0cdc6 !important;
}

.leaflet-control-zoom a:hover {
  background-color: #faf8f4 !important;
}

.leaflet-control-attribution {
  font-size: 8px !important;
  background: rgba(255, 255, 255, 0.7) !important;
  padding: 1px 4px !important;
  border-radius: 2px !important;
  color: #9d998f !important;
}

.leaflet-control-attribution a {
  color: #9d998f !important;
}
</style>

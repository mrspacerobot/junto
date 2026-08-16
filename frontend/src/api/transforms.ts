import type {
  ActivityCreateRequest,
  ActivityDetailResponse,
  ActivityListItemResponse,
  ActivityTypeResponse,
  MessageThreadResponse,
  ParticipantResponse,
} from './generated/types.gen';

export type CustomFieldValue = string | number | boolean | DateRangeValue;
export interface DateRangeValue {
  start: string;
  end: string;
}

export interface ActivityTypeFieldOption {
  name: string;
  label: string;
  type: string;
  required: boolean;
  options: string[];
  unit?: string;
}

export interface ActivityTypeOption {
  slug: string;
  name: string;
  customFields: ActivityTypeFieldOption[];
}

export interface ActivityDetail {
  id: string;
  title: string;
  description?: string;
  activityTypeName: string;
  activityTypeSlug: string;
  city?: string;
  startsAt: string;
  signupDeadline?: string;
  maxParticipants: number;
  currentParticipants: number;
  joinPolicy: 'OPEN' | 'APPROVAL_REQUIRED';
  status: 'ACTIVE' | 'CANCELLED' | 'COMPLETED';
  customFields: Record<string, unknown>;
  imageUrl?: string;
  creatorId: string;
  creatorDisplayName: string;
  creatorAvatarUrl?: string;
  isJoinable: boolean;
  effectiveDeadline: string;
}

export interface ActivityListItem {
  id: string;
  title: string;
  activityTypeName: string;
  activityTypeSlug: string;
  activityTypeIconName?: string;
  city?: string;
  locationLat?: number;
  locationLng?: number;
  startsAt: string;
  maxParticipants: number;
  currentParticipants: number;
  status: 'ACTIVE' | 'CANCELLED' | 'COMPLETED';
  creatorDisplayName: string;
  creatorAvatarUrl?: string;
  customFields: Record<string, unknown>;
  imageUrl?: string;
  participantAvatars: string[];
}

export interface Participant {
  id: string;
  userId: string;
  displayName: string;
  avatarUrl?: string;
  role: 'CREATOR' | 'PARTICIPANT';
  status: 'PENDING' | 'APPROVED' | 'DECLINED';
}

export interface ActivityFormValues {
  title: string;
  description: string;
  typeSlug: string;
  city: string;
  startsAt: string;
  signupDeadline: string;
  maxParticipants: number;
  joinPolicy: 'OPEN' | 'APPROVAL_REQUIRED';
  customFields: Record<string, CustomFieldValue>;
}

export function toActivityTypeOption(response: ActivityTypeResponse): ActivityTypeOption {
  if (!response.slug || !response.name) {
    throw new Error('Activity type response is missing its stable slug or name');
  }
  return {
    slug: response.slug,
    name: response.name,
    customFields: (response.customFields ?? [])
      .map((field) => ({
        name: field.name ?? '',
        label: field.label ?? field.name ?? '',
        type: field.type ?? 'TEXT',
        required: field.required ?? false,
        options: field.options ?? [],
        unit: field.unit,
      }))
      .filter((field) => field.name.length > 0),
  };
}

export function toActivityDetail(response: ActivityDetailResponse): ActivityDetail {
  if (
    !response.id ||
    !response.title ||
    !response.activityTypeSlug ||
    !response.startsAt ||
    !response.creatorId ||
    !response.effectiveDeadline
  ) {
    throw new Error('Activity response is missing required detail metadata');
  }
  return {
    id: response.id,
    title: response.title,
    description: response.description,
    activityTypeName: response.activityTypeName ?? response.activityTypeSlug,
    activityTypeSlug: response.activityTypeSlug,
    city: response.city,
    startsAt: response.startsAt,
    signupDeadline: response.signupDeadline,
    maxParticipants: response.maxParticipants ?? 0,
    currentParticipants: response.currentParticipants ?? 0,
    joinPolicy: response.joinPolicy ?? 'OPEN',
    status: response.status ?? 'ACTIVE',
    customFields: response.customFields ?? {},
    imageUrl: response.imageUrl,
    creatorId: response.creatorId,
    creatorDisplayName: response.creatorDisplayName ?? 'Junto member',
    creatorAvatarUrl: response.creatorAvatarUrl,
    isJoinable: response.isJoinable ?? false,
    effectiveDeadline: response.effectiveDeadline,
  };
}

export function toActivityListItem(response: ActivityListItemResponse): ActivityListItem {
  if (!response.id || !response.title || !response.activityTypeSlug || !response.startsAt) {
    throw new Error('Activity response is missing required card metadata');
  }
  return {
    id: response.id,
    title: response.title,
    activityTypeName: response.activityTypeName ?? response.activityTypeSlug,
    activityTypeSlug: response.activityTypeSlug,
    activityTypeIconName: response.activityTypeIconName,
    city: response.city,
    locationLat: response.locationLat,
    locationLng: response.locationLng,
    startsAt: response.startsAt,
    maxParticipants: response.maxParticipants ?? 0,
    currentParticipants: response.currentParticipants ?? 0,
    status: response.status ?? 'ACTIVE',
    creatorDisplayName: response.creatorDisplayName ?? 'Junto member',
    creatorAvatarUrl: response.creatorAvatarUrl,
    customFields: response.customFields ?? {},
    imageUrl: response.imageUrl,
    participantAvatars: response.participantAvatars ?? [],
  };
}

export function toParticipant(response: ParticipantResponse): Participant {
  if (!response.id || !response.userId) {
    throw new Error('Participant response is missing an identifier');
  }
  return {
    id: response.id,
    userId: response.userId,
    displayName: response.displayName ?? 'Junto member',
    avatarUrl: response.avatarUrl,
    role: response.role ?? 'PARTICIPANT',
    status: response.status ?? 'PENDING',
  };
}

export function toMessageThread(response: MessageThreadResponse) {
  if (!response.activityId || !response.activityTitle || !response.activityTypeSlug) {
    throw new Error('Message thread response is missing required activity metadata');
  }
  return {
    activityId: response.activityId,
    activityTitle: response.activityTitle,
    activityTypeSlug: response.activityTypeSlug,
    activityTypeName: response.activityTypeName ?? response.activityTypeSlug,
    imageUrl: response.imageUrl ?? null,
    participantCount: response.participantCount ?? 0,
    participantAvatars: response.participantAvatars ?? [],
    lastMessageContent: response.lastMessageContent ?? null,
    lastMessageSender: response.lastMessageSender ?? null,
    lastMessageAt: response.lastMessageAt ?? null,
  };
}

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (typeof error !== 'object' || error === null) return fallback;
  const body = error as { error?: { message?: unknown }; message?: unknown };
  if (typeof body.error?.message === 'string') return body.error.message;
  return typeof body.message === 'string' ? body.message : fallback;
}

export function toActivityCreateRequest(form: ActivityFormValues): ActivityCreateRequest {
  return {
    title: form.title.trim(),
    description: form.description.trim() || undefined,
    typeSlug: form.typeSlug,
    city: form.city.trim() || undefined,
    startsAt: new Date(form.startsAt).toISOString(),
    signupDeadline: form.signupDeadline ? new Date(form.signupDeadline).toISOString() : undefined,
    maxParticipants: form.maxParticipants,
    joinPolicy: form.joinPolicy,
    customFields: form.customFields,
  };
}

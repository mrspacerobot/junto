import { createRouter, createWebHistory } from 'vue-router';
import AppShell from '@/components/layout/AppShell.vue';
import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/auth/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/auth/verify',
      name: 'verify-magic-link',
      component: () => import('@/views/auth/VerifyMagicLinkView.vue'),
      meta: { public: true, allowAuthenticated: true },
    },
    {
      path: '/auth/onboard',
      name: 'onboard',
      component: () => import('@/views/auth/OnboardView.vue'),
    },
    {
      path: '/',
      component: AppShell,
      children: [
        {
          path: '',
          redirect: '/activities',
        },
        {
          path: 'activities',
          name: 'activities',
          component: () => import('@/views/activities/ActivityFeedView.vue'),
        },
        {
          path: 'activities/:id',
          name: 'activity-detail',
          component: () => import('@/views/activities/ActivityDetailView.vue'),
          props: true,
        },
        {
          path: 'activities/new',
          name: 'activity-create',
          component: () => import('@/views/activities/ActivityCreateView.vue'),
        },
        {
          path: 'explore',
          name: 'explore',
          component: () => import('@/views/explore/ExploreView.vue'),
        },
        {
          path: 'saved',
          name: 'saved',
          component: () => import('@/views/saved/SavedView.vue'),
        },
        {
          path: 'messages',
          name: 'messages',
          component: () => import('@/views/messages/MessagesView.vue'),
        },
        {
          path: 'messages/:activityId',
          name: 'messages-chat',
          component: () => import('@/views/messages/MessagesView.vue'),
          props: true,
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/profile/ProfileView.vue'),
        },
      ],
    },
  ],
});

router.beforeEach(async (to, _from, next) => {
  const auth = useAuthStore();

  if (!auth.initialized) {
    await auth.fetchMe();
  }

  if (to.meta.public) {
    if (to.meta.allowAuthenticated) return next();
    if (auth.isAuthenticated) {
      if (!auth.isOnboarded) return next('/auth/onboard');
      return next('/activities');
    }
    return next();
  }

  if (!auth.isAuthenticated) {
    return next('/auth/login');
  }

  if (!auth.isOnboarded && to.name !== 'onboard') {
    return next('/auth/onboard');
  }

  next();
});

export default router;

import { client } from './generated/client.gen';

// Use Vite proxy instead of hardcoded backend URL
client.setConfig({ baseUrl: '' });

// CSRF Middleware
client.interceptors.request.use((request) => {
  const xsrfToken = document.cookie
    .split('; ')
    .find((row) => row.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];

  if (xsrfToken) {
    request.headers.set('X-XSRF-TOKEN', xsrfToken);
  }
  return request;
});

// Auth redirect on 401
client.interceptors.response.use((response) => {
  if (response.status === 401 && !window.location.pathname.startsWith('/auth/')) {
    window.location.href = '/auth/login';
  }
  return response;
});

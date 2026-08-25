import { defineConfig } from '@playwright/test';

const backendPort = Number(process.env.E2E_BACKEND_PORT ?? 8080);
const backendUrl = `http://localhost:${backendPort}`;
const frontendPort = Number(process.env.E2E_FRONTEND_PORT ?? 5173);
const frontendUrl = `http://localhost:${frontendPort}`;

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: process.env.CI ? 1 : 0,
  webServer: [
    {
      command: './gradlew bootRun --args="--spring.profiles.active=dev"',
      cwd: '../backend',
      url: `${backendUrl}/v3/api-docs`,
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
      env: {
        SERVER_PORT: String(backendPort),
        APP_ALLOWED_ORIGIN: frontendUrl,
        APP_BASE_URL: frontendUrl,
      },
    },
    {
      command: `npm run dev -- --host 127.0.0.1 --port ${frontendPort} --strictPort`,
      cwd: '.',
      url: `${frontendUrl}/auth/login`,
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
    },
  ],
  use: {
    baseURL: frontendUrl,
    viewport: { width: 1280, height: 900 },
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' },
    },
  ],
});

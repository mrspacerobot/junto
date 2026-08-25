import { expect, test, type Page, type Request } from '@playwright/test';
import { login } from './auth';

const packagedAppUrl = process.env.PACKAGED_APP_URL ?? 'http://localhost:8080';
const externalImage = `
  <svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256">
    <rect width="256" height="256" fill="#d4e4c8"/>
    <path d="M0 32h256M0 96h256M0 160h256M0 224h256M32 0v256M96 0v256M160 0v256M224 0v256" stroke="#3d5a1e"/>
  </svg>`;

const isRequiredExternalResource = (request: Request) => {
  const hostname = new URL(request.url()).hostname;
  return hostname === 'images.unsplash.com' || hostname.endsWith('.basemaps.cartocdn.com');
};

async function createMappedActivity(page: Page) {
  const startsAt = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
  const signupDeadline = new Date(Date.now() + 12 * 60 * 60 * 1000).toISOString();
  const result = await page.evaluate(
    async ({ startsAt, signupDeadline }) => {
      const tokenMatch = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
      const csrf = tokenMatch ? decodeURIComponent(tokenMatch[1]) : '';
      const response = await fetch('/api/activities', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
        },
        body: JSON.stringify({
          title: `CSP browser evidence ${Date.now()}`,
          description: 'Verifies production image and map policy.',
          typeSlug: 'cycling',
          city: 'Zurich',
          locationLat: 47.3769,
          locationLng: 8.5417,
          startsAt,
          signupDeadline,
          maxParticipants: 6,
          joinPolicy: 'OPEN',
          customFields: { distance: 20, difficulty: 'Beginner' },
        }),
      });
      return { status: response.status, body: await response.text() };
    },
    { startsAt, signupDeadline },
  );
  expect(result.status, result.body).toBe(200);
}

test('packaged app security policy protects referrers while maps and images render', async ({
  page,
}, testInfo) => {
  const externalRequests: Request[] = [];
  const externalFailures: string[] = [];
  const cspErrors: string[] = [];

  await page.route('https://fonts.googleapis.com/**', (route) =>
    route.fulfill({ status: 200, contentType: 'text/css', body: '' }),
  );
  await page.route('https://images.unsplash.com/**', (route) =>
    route.fulfill({ status: 200, contentType: 'image/svg+xml', body: externalImage }),
  );
  await page.route('https://*.basemaps.cartocdn.com/**', (route) =>
    route.fulfill({ status: 200, contentType: 'image/svg+xml', body: externalImage }),
  );

  page.on('request', (request) => {
    if (isRequiredExternalResource(request)) externalRequests.push(request);
  });
  page.on('requestfailed', (request) => {
    // Leaflet aborts obsolete zoom-level tiles after fitBounds recalculates the
    // viewport. Keep CSP and transport failures fatal while ignoring that normal
    // cancellation path.
    if (
      isRequiredExternalResource(request) &&
      request.failure()?.errorText !== 'net::ERR_ABORTED'
    ) {
      externalFailures.push(request.url());
    }
  });
  page.on('console', (message) => {
    if (message.type() === 'error' && message.text().includes('Content Security Policy')) {
      cspErrors.push(message.text());
    }
  });

  await login(page, 'anna@example.com', packagedAppUrl);
  await createMappedActivity(page);
  const response = await page.goto(`${packagedAppUrl}/activities`);
  expect(response).not.toBeNull();
  expect(response!.headers()['referrer-policy']).toBe('no-referrer');
  expect(response!.headers()['x-frame-options']).toBe('DENY');
  expect(response!.headers()['x-content-type-options']).toBe('nosniff');
  expect(response!.headers()['strict-transport-security']).toBeUndefined();
  expect(response!.headers()['content-security-policy']).toContain(
    "img-src 'self' data: https://images.unsplash.com https://a.basemaps.cartocdn.com",
  );

  const activityImage = page.locator('img[src*="images.unsplash.com"]').first();
  await expect(activityImage).toBeVisible();
  await expect
    .poll(() => activityImage.evaluate((image: HTMLImageElement) => image.naturalWidth))
    .toBeGreaterThan(0);

  const mapTile = page.locator('img.leaflet-tile-loaded').first();
  await expect(mapTile).toBeVisible();
  await expect
    .poll(() => mapTile.evaluate((image: HTMLImageElement) => image.naturalWidth))
    .toBe(256);

  expect(externalRequests.some((request) => request.url().includes('images.unsplash.com'))).toBe(
    true,
  );
  expect(externalRequests.some((request) => request.url().includes('.basemaps.cartocdn.com'))).toBe(
    true,
  );
  for (const request of externalRequests) {
    expect(request.headers().referer).toBeUndefined();
  }
  expect(externalFailures).toEqual([]);
  expect(cspErrors).toEqual([]);

  const screenshot = await page.screenshot({ fullPage: true });
  await testInfo.attach('csp-map-and-images.png', {
    body: screenshot,
    contentType: 'image/png',
  });
  await testInfo.attach('browser-security-evidence.json', {
    body: Buffer.from(
      JSON.stringify(
        {
          responseHeaders: {
            referrerPolicy: response!.headers()['referrer-policy'],
            xFrameOptions: response!.headers()['x-frame-options'],
            xContentTypeOptions: response!.headers()['x-content-type-options'],
            strictTransportSecurity: response!.headers()['strict-transport-security'] ?? null,
            contentSecurityPolicy: response!.headers()['content-security-policy'],
          },
          requestedExternalHosts: [
            ...new Set(externalRequests.map((request) => new URL(request.url()).hostname)),
          ],
          externalRequestsWithReferer: externalRequests
            .filter((request) => request.headers().referer !== undefined)
            .map((request) => request.url()),
          externalFailures,
          cspErrors,
          renderedActivityImageNaturalWidth: await activityImage.evaluate(
            (image: HTMLImageElement) => image.naturalWidth,
          ),
          renderedMapTileNaturalWidth: await mapTile.evaluate(
            (image: HTMLImageElement) => image.naturalWidth,
          ),
        },
        null,
        2,
      ),
    ),
    contentType: 'application/json',
  });
});

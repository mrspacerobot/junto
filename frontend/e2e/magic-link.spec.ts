import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { login } from './auth';

const mailpitUrl = process.env.MAILPIT_URL ?? 'http://localhost:8025';
const uniqueEmail = (prefix = 'magic') =>
  `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}@example.com`;

interface MailpitMessage {
  ID: string;
  To?: Array<{ Address?: string }>;
}

async function waitForAuthEmail(request: APIRequestContext, email: string) {
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline) {
    const listResponse = await request.get(`${mailpitUrl}/api/v1/messages`);
    if (listResponse.ok()) {
      const listing = (await listResponse.json()) as { messages?: MailpitMessage[] };
      const match = listing.messages?.find((message) =>
        message.To?.some((recipient) => recipient.Address === email),
      );
      if (match) {
        const messageResponse = await request.get(`${mailpitUrl}/api/v1/message/${match.ID}`);
        const message = (await messageResponse.json()) as { Text?: string };
        if (message.Text) return message.Text;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`Authentication email was not delivered to exact recipient ${email}`);
}

async function logout(page: Page) {
  await page.evaluate(async () => {
    await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
  });
}

async function createInvite(page: Page): Promise<string> {
  await login(page, 'system@junto.ch');
  const result = await page.evaluate(async () => {
    const response = await fetch('/api/invites', {
      method: 'POST',
      credentials: 'include',
    });
    return { status: response.status, body: (await response.json()) as { code?: string } };
  });
  if (result.status !== 200 || !result.body.code) {
    throw new Error(`Could not create test invite: ${result.status}`);
  }
  await logout(page);
  return result.body.code;
}

test('create-account intent requires an invite before any request is sent', async ({ page }) => {
  let signupRequests = 0;
  page.on('request', (request) => {
    if (request.url().includes('/api/auth/signup/request-code')) signupRequests++;
  });

  await page.goto('/auth/login');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByLabel('Email address').fill(uniqueEmail('missing-invite'));
  await page.locator('form').getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByRole('alert')).toHaveText('Enter your invite code to create an account.');
  await expect(page.getByLabel('Invite Code')).toBeVisible();
  expect(signupRequests).toBe(0);
});

test('invalid invite produces an actionable signup error', async ({ page }) => {
  await page.goto('/auth/login');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByLabel('Email address').fill(uniqueEmail('invalid-invite'));
  await page.getByLabel('Invite Code').fill('NOT-A-VALID-INVITE');
  await page.locator('form').getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByRole('alert')).toHaveText(
    'This invite is invalid or unavailable. Ask for a new invite.',
  );
  await expect(page.getByLabel('Verification Code')).toHaveCount(0);
});

test('existing-account sign-in remains invite-free through real email and magic link', async ({
  page,
  request,
}, testInfo) => {
  const email = uniqueEmail('existing-signin');
  await login(page, email);
  await logout(page);

  await page.goto('/auth/login');
  await page.getByLabel('Email address').fill(email);
  await page.getByRole('button', { name: 'Send sign-in code' }).click();

  await expect(page.getByText(`If an account exists for ${email}`)).toBeVisible();
  const text = await waitForAuthEmail(request, email);
  const magicLink = text.match(/https?:\/\/[^\s]+\/auth\/verify#token=[^\s]+/)?.[0];
  expect(magicLink).toBeTruthy();

  const token = new URL(magicLink!).hash.slice('#token='.length);
  const serverVisibleRequestUrls: string[] = [];
  const externalReferrers: string[] = [];
  let verificationPageUrlAtExchange = '';
  page.on('request', (browserRequest) => {
    const requestUrl = new URL(browserRequest.url());
    if (requestUrl.origin === new URL(magicLink!).origin) {
      serverVisibleRequestUrls.push(browserRequest.url());
    } else {
      externalReferrers.push(browserRequest.headers().referer ?? '');
    }
    if (requestUrl.pathname === '/api/auth/verify-token') {
      verificationPageUrlAtExchange = browserRequest.frame().url();
    }
  });

  const historyLengthBeforeMagicLink = await page.evaluate(() => window.history.length);
  await page.goto(magicLink!);
  await expect(page).toHaveURL(/\/activities$/, { timeout: 15_000 });
  expect(serverVisibleRequestUrls).not.toContainEqual(expect.stringContaining(token));
  expect(verificationPageUrlAtExchange).toBe('http://localhost:5173/auth/verify');
  expect(externalReferrers).not.toContainEqual(expect.stringContaining(token));
  expect(await page.evaluate(() => JSON.stringify(window.history.state))).not.toContain(token);
  await expect
    .poll(() => page.evaluate(() => window.history.length))
    .toBe(historyLengthBeforeMagicLink + 1);

  await page.goBack();
  await expect(page).not.toHaveURL(new RegExp(token));

  await logout(page);
  await page.goto(magicLink!);
  await expect(page.getByRole('alert')).toHaveText(
    'This link is invalid, expired, or has already been used.',
  );
  expect(await page.evaluate(() => JSON.stringify(window.history.state))).not.toContain(token);

  const historyLengthBeforeSameRouteLink = await page.evaluate(() => window.history.length);
  const sameRouteToken = 'same-route-fragment-secret';
  await page.evaluate((fragmentToken) => {
    window.location.hash = `token=${fragmentToken}`;
  }, sameRouteToken);
  await expect(page).toHaveURL('http://localhost:5173/auth/verify');
  expect(await page.evaluate(() => JSON.stringify(window.history.state))).not.toContain(
    sameRouteToken,
  );
  expect(await page.evaluate(() => window.history.length)).toBe(
    historyLengthBeforeSameRouteLink + 1,
  );

  await page.goBack();
  await expect(page).toHaveURL('http://localhost:5173/auth/verify');
  expect(await page.evaluate(() => JSON.stringify(window.history.state))).not.toContain(
    sameRouteToken,
  );

  await page.getByRole('link', { name: 'Request a new link' }).click();
  await expect(page).toHaveURL('http://localhost:5173/auth/login');

  const parsedMagicLink = new URL(magicLink!);
  await testInfo.attach('magic-link-leak-evidence.json', {
    body: Buffer.from(
      JSON.stringify(
        {
          deliveredLink: `${parsedMagicLink.origin}${parsedMagicLink.pathname}#token=[redacted]`,
          deliveredLinkQuery: parsedMagicLink.search || null,
          providerVisibleNavigationUrl: serverVisibleRequestUrls.find(
            (url) => new URL(url).pathname === '/auth/verify',
          ),
          serverVisibleRequestUrlsContainingToken: serverVisibleRequestUrls.filter((url) =>
            url.includes(token),
          ),
          verificationPageUrlAtExchange,
          thirdPartyReferrersContainingToken: externalReferrers.filter((referer) =>
            referer.includes(token),
          ),
          retainedHistoryStateContainsToken: false,
          backNavigationRestoredToken: false,
          initialNavigationHistoryLengthDelta: 1,
          sameRouteFragmentNavigation: {
            exercisedViaHashChangeOnMountedView: true,
            sanitizedUrl: 'http://localhost:5173/auth/verify',
            retainedHistoryStateContainsToken: false,
            backNavigationRestoredToken: false,
            historyLengthDelta: 1,
          },
          singleUseReplayRejected: true,
        },
        null,
        2,
      ),
    ),
    contentType: 'application/json',
  });
});

test('valid invite emails the exact address, creates the account, and becomes unavailable', async ({
  page,
  request,
}) => {
  const inviteCode = await createInvite(page);
  const email = uniqueEmail('valid-signup');

  await page.goto('/auth/login');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Invite Code').fill(inviteCode);
  await page.locator('form').getByRole('button', { name: 'Create account' }).click();

  const text = await waitForAuthEmail(request, email);
  const code = text.match(/login code: (\d{6})/)?.[1];
  expect(code).toMatch(/^\d{6}$/);
  await page.getByLabel('Verification Code').fill(code!);
  await page.getByRole('button', { name: 'Verify & Sign In' }).click();
  await expect(page).toHaveURL(/\/auth\/onboard$/, { timeout: 15_000 });

  const me = await page.evaluate(async () => {
    const response = await fetch('/api/auth/me', { credentials: 'include' });
    return response.json() as Promise<{ email?: string }>;
  });
  expect(me.email).toBe(email);

  await logout(page);
  await page.goto('/auth/login');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByLabel('Email address').fill(uniqueEmail('used-invite'));
  await page.getByLabel('Invite Code').fill(inviteCode);
  await page.locator('form').getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('alert')).toHaveText(
    'This invite is invalid or unavailable. Ask for a new invite.',
  );
});

test('request rate-limit failure remains visible instead of advancing', async ({ page }) => {
  const email = uniqueEmail('rate-visible');
  await login(page, email);
  await logout(page);
  await page.goto('/auth/login');
  await page.getByLabel('Email address').fill(email);

  for (let attempt = 0; attempt < 3; attempt++) {
    await page.getByRole('button', { name: 'Send sign-in code' }).click();
    await expect(page.getByLabel('Verification Code')).toBeVisible();
    await page.getByRole('button', { name: 'Back to email' }).click();
  }

  await page.getByRole('button', { name: 'Send sign-in code' }).click();
  await expect(page.getByRole('alert')).toHaveText(
    'Too many authentication attempts. Try again later.',
  );
  await expect(page.getByLabel('Verification Code')).toHaveCount(0);
});

test('SMTP delivery failure is actionable in create-account mode', async ({ page }) => {
  await page.route('**/api/auth/signup/request-code', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({
        error: {
          code: 'EMAIL_DELIVERY_FAILED',
          message: "We couldn't send your code. Please try again.",
          status: 503,
        },
      }),
    }),
  );

  await page.goto('/auth/login');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByLabel('Email address').fill(uniqueEmail('smtp-failure'));
  await page.getByLabel('Invite Code').fill('JUNTO-TEST');
  await page.locator('form').getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByRole('alert')).toHaveText("We couldn't send your code. Please try again.");
  await expect(page.getByLabel('Verification Code')).toHaveCount(0);
});

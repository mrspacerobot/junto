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
}) => {
  const email = uniqueEmail('existing-signin');
  await login(page, email);
  await logout(page);

  await page.goto('/auth/login');
  await page.getByLabel('Email address').fill(email);
  await page.getByRole('button', { name: 'Send sign-in code' }).click();

  await expect(page.getByText(`If an account exists for ${email}`)).toBeVisible();
  const text = await waitForAuthEmail(request, email);
  const magicLink = text.match(/http:\/\/localhost:5173\/auth\/verify\?token=[^\s]+/)?.[0];
  expect(magicLink).toBeTruthy();

  await page.goto(magicLink!);
  await expect(page).toHaveURL(/\/activities$/, { timeout: 15_000 });
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

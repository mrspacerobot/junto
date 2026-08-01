import { type Page } from '@playwright/test'

/**
 * Log in via the dev-only /api/auth/dev-login endpoint.
 * Only works when the backend is running with the "dev" profile.
 *
 * Uses the browser's fetch so the session cookie lands in the
 * browser's own cookie jar on the correct origin.
 */
export async function login(page: Page, email: string) {
  // Navigate to the app — may redirect to login page, that's fine
  await page.goto('/auth/login')
  await page.waitForLoadState('domcontentloaded')

  // Get CSRF token from cookies (set by Spring Security)
  const csrf = await page.evaluate(() => {
    const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
    return match ? decodeURIComponent(match[1]) : ''
  })

  // Use browser fetch so the session cookie lands in the browser jar
  const result = await page.evaluate(async ({ email, csrf }) => {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    }
    if (csrf) {
      headers['X-XSRF-TOKEN'] = csrf
    }

    const res = await fetch('/api/auth/dev-login', {
      method: 'POST',
      headers,
      body: JSON.stringify({ email }),
      credentials: 'include',
    })
    return { ok: res.ok, status: res.status }
  }, { email, csrf })

  if (!result.ok) {
    throw new Error(`dev-login failed for ${email}: ${result.status}`)
  }
}

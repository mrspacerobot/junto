import { test, expect, type Page } from '@playwright/test'
import { login } from './auth'

const uniqueEmail = (name: string) =>
  `${name}-${Date.now()}-${Math.random().toString(36).slice(2)}@example.com`

const threadRows = (page: Page) => page.getByTestId('message-thread-row')
const chatInput = (page: Page) => page.getByTestId('chat-message-input')
const activityCards = (page: Page) => page.getByTestId('activity-card')

async function createActivity(page: Page, title: string): Promise<{ id: string }> {
  await page.goto('/auth/login')
  await page.waitForLoadState('domcontentloaded')

  const startsAt = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()
  const signupDeadline = new Date(Date.now() + 12 * 60 * 60 * 1000).toISOString()

  const result = await page.evaluate(
    async ({ title, startsAt, signupDeadline }) => {
      const tokenMatch = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
      const csrf = tokenMatch ? decodeURIComponent(tokenMatch[1]) : ''

      const res = await fetch('/api/activities', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
        },
        body: JSON.stringify({
          title,
          description: 'Created by Playwright',
          typeSlug: 'cycling',
          city: 'Zurich',
          locationLat: 47.3769,
          locationLng: 8.5417,
          startsAt,
          signupDeadline,
          maxParticipants: 6,
          joinPolicy: 'OPEN',
          customFields: {
            distance: 20,
            difficulty: 'Beginner',
          },
        }),
      })

      if (!res.ok) {
        return { ok: false, status: res.status, body: await res.text() }
      }
      return { ok: true, status: res.status, body: await res.json() }
    },
    { title, startsAt, signupDeadline },
  )

  if (!result.ok) {
    throw new Error(`Activity creation failed: ${result.status} ${result.body}`)
  }
  return result.body as { id: string }
}

async function joinActivity(page: Page, activityId: string) {
  await page.goto('/auth/login')
  await page.waitForLoadState('domcontentloaded')

  const result = await page.evaluate(async ({ activityId }) => {
    const tokenMatch = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
    const csrf = tokenMatch ? decodeURIComponent(tokenMatch[1]) : ''
    const res = await fetch(`/api/activities/${activityId}/participants/join`, {
      method: 'POST',
      credentials: 'include',
      headers: csrf ? { 'X-XSRF-TOKEN': csrf } : {},
    })
    return { ok: res.ok, status: res.status, body: await res.text() }
  }, { activityId })

  if (!result.ok) {
    throw new Error(`Join activity failed: ${result.status} ${result.body}`)
  }
}

async function openFirstThread(page: Page) {
  await page.goto('/messages')
  await page.waitForLoadState('domcontentloaded')
  await expect(threadRows(page).first()).toBeVisible()
  await threadRows(page).first().click()
  await expect(chatInput(page)).toBeVisible()
}

test.describe('Two-user chat', () => {
  test('two users can chat in real time', async ({ browser }) => {
    const annaCtx = await browser.newContext()
    const marcoCtx = await browser.newContext()
    const anna = await annaCtx.newPage()
    const marco = await marcoCtx.newPage()

    try {
      const annaEmail = uniqueEmail('anna')
      const marcoEmail = uniqueEmail('marco')

      await login(anna, annaEmail)
      await login(marco, marcoEmail)

      const activity = await createActivity(anna, `Two-user chat ${Date.now()}`)
      await joinActivity(marco, activity.id)

      await openFirstThread(anna)
      await openFirstThread(marco)

      await chatInput(anna).fill('Hey Marco, ready for the ride?')
      await anna.keyboard.press('Enter')
      await expect(anna.getByText('Hey Marco, ready for the ride?')).toHaveCount(1)

      await expect(marco.getByText('Hey Marco, ready for the ride?')).toHaveCount(1, {
        timeout: 5000,
      })

      await chatInput(marco).fill("Let's go! I'll bring coffee.")
      await marco.keyboard.press('Enter')
      await expect(marco.getByText("Let's go! I'll bring coffee.")).toHaveCount(1)

      await expect(anna.getByText("Let's go! I'll bring coffee.")).toHaveCount(1, {
        timeout: 5000,
      })
    } finally {
      await annaCtx.close()
      await marcoCtx.close()
    }
  })
})

test.describe('Messages view', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeEach(async ({ page }, testInfo) => {
    await login(page, uniqueEmail(`messages-${testInfo.parallelIndex}`))
  })

  test('shows messages layout with thread list and empty state', async ({ page }) => {
    await page.goto('/messages')
    await page.waitForLoadState('domcontentloaded')
    await expect(page.locator('h1:has-text("Messages")')).toBeVisible()
    await expect(page.locator('input[placeholder="Search messages"]')).toBeVisible()
    await expect(page.getByText('Select a conversation')).toBeVisible()
  })

  test('joins an activity to create a message thread', async ({ page }) => {
    await createActivity(page, `Join-thread ${Date.now()}`)
    await page.goto('/activities')
    await page.waitForLoadState('domcontentloaded')
    await expect(activityCards(page).first()).toBeVisible()
    await activityCards(page).first().click()
    await page.goto('/messages')
    await expect(threadRows(page).first()).toBeVisible()
  })

  test('can deep-link directly to a thread while thread metadata loads', async ({ page }) => {
    const activity = await createActivity(page, `Open-thread ${Date.now()}`)
    await page.goto(`/messages/${activity.id}`)
    await expect(chatInput(page)).toBeVisible()
  })

  test('can send a message in a thread', async ({ page }) => {
    await createActivity(page, `Send-message ${Date.now()}`)
    await openFirstThread(page)
    await chatInput(page).fill('Hello from Playwright!')
    await page.keyboard.press('Enter')
    await expect(page.getByText('Hello from Playwright!').first()).toBeVisible()
  })

  test('search filters thread list', async ({ page }) => {
    await createActivity(page, `Searchable thread ${Date.now()}`)
    await page.goto('/messages')
    await page.waitForLoadState('domcontentloaded')
    const searchInput = page.locator('input[placeholder="Search messages"]')
    await searchInput.fill('nonexistent-thread-xyz')
    await expect(threadRows(page)).toHaveCount(0)
  })
})

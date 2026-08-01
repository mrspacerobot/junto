import { expect, test, type APIRequestContext } from '@playwright/test'
import { login } from './auth'

const uniqueEmail = () =>
  `magic-${Date.now()}-${Math.random().toString(36).slice(2)}@example.com`

interface MailpitMessage {
  ID: string
  To?: Array<{ Address?: string }>
}

async function waitForMagicLink(request: APIRequestContext, email: string): Promise<string> {
  const deadline = Date.now() + 15_000
  while (Date.now() < deadline) {
    const listResponse = await request.get('http://localhost:8025/api/v1/messages')
    if (listResponse.ok()) {
      const listing = await listResponse.json() as { messages?: MailpitMessage[] }
      const match = listing.messages?.find(message =>
        message.To?.some(recipient => recipient.Address === email),
      )
      if (match) {
        const messageResponse = await request.get(`http://localhost:8025/api/v1/message/${match.ID}`)
        const message = await messageResponse.json() as { Text?: string }
        const link = message.Text?.match(/http:\/\/localhost:5173\/auth\/verify\?token=[^\s]+/)?.[0]
        if (link) return link
      }
    }
    await new Promise(resolve => setTimeout(resolve, 250))
  }
  throw new Error(`Magic-link email was not delivered to ${email}`)
}

test('a customer can complete the emailed magic-link flow', async ({ page, request }) => {
  const email = uniqueEmail()
  await login(page, email)

  await page.evaluate(async () => {
    await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' })
  })
  await page.goto('/auth/login')

  const requestResult = await page.evaluate(async (targetEmail) => {
    const response = await fetch('/api/auth/request-code', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: targetEmail }),
    })
    return response.status
  }, email)
  expect(requestResult).toBe(200)

  const magicLink = await waitForMagicLink(request, email)
  await page.goto(magicLink)
  await expect(page).toHaveURL(/\/activities$/, { timeout: 15_000 })
  await expect(page.getByText('Signing you in to Junto')).toHaveCount(0)
})

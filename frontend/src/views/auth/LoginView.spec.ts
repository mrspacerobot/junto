import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginView from './LoginView.vue'

const sdk = vi.hoisted(() => ({
  requestCode: vi.fn(),
  requestSignupCode: vi.fn(),
  verify: vi.fn(),
}))

vi.mock('@/api/generated/sdk.gen', () => ({
  authRequestCode: sdk.requestCode,
  authRequestSignupCode: sdk.requestSignupCode,
  authVerify: sdk.verify,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ fetchMe: vi.fn(), isOnboarded: false }),
}))

const mountView = () => mount(LoginView, {
  global: {
    stubs: { JuntoLogo: true },
  },
})

const button = (wrapper: ReturnType<typeof mountView>, label: string) => {
  const match = wrapper.findAll('button').find(candidate => candidate.text() === label)
  if (!match) throw new Error(`Button not found: ${label}`)
  return match
}

const apiError = (message: string) => ({ error: { error: { message } } })

describe('LoginView account intent', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sdk.requestCode.mockResolvedValue({ error: undefined })
    sdk.requestSignupCode.mockResolvedValue({ error: undefined })
  })

  it('requires an invite immediately in create-account mode without sending a request', async () => {
    const wrapper = mountView()
    await button(wrapper, 'Create account').trigger('click')
    await wrapper.get('#email').setValue('new@example.com')

    expect(wrapper.text()).toContain('A valid invite is required for every new account.')
    expect(wrapper.find('label[for="inviteCode"]').text()).toBe('Invite Code')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('[role="alert"]').text()).toBe('Enter your invite code to create an account.')
    expect(sdk.requestSignupCode).not.toHaveBeenCalled()
    expect(sdk.requestCode).not.toHaveBeenCalled()
  })

  it('keeps invite-free sign-in separate from valid-invite signup', async () => {
    const signIn = mountView()
    await signIn.get('#email').setValue('existing@example.com')
    await signIn.get('form').trigger('submit')
    await flushPromises()

    expect(sdk.requestCode).toHaveBeenCalledWith({ body: { email: 'existing@example.com' } })
    expect(sdk.requestSignupCode).not.toHaveBeenCalled()
    expect(signIn.text()).toContain('If an account exists for existing@example.com')

    vi.clearAllMocks()
    const signup = mountView()
    await button(signup, 'Create account').trigger('click')
    await signup.get('#email').setValue('new@example.com')
    await signup.get('#inviteCode').setValue(' JUNTO-VALID ')
    await signup.get('form').trigger('submit')
    await flushPromises()

    expect(sdk.requestSignupCode).toHaveBeenCalledWith({
      body: { email: 'new@example.com', inviteCode: 'JUNTO-VALID' },
    })
    expect(sdk.requestCode).not.toHaveBeenCalled()
    expect(signup.text()).toContain('sent to new@example.com to finish creating your account')
  })

  it.each([
    ['This invite is invalid or unavailable. Ask for a new invite.', 'signup'],
    ["We couldn't send your code. Please try again.", 'signup'],
    ['Too many authentication attempts. Try again later.', 'signin'],
  ])('shows actionable API failure: %s', async (message, flow) => {
    const wrapper = mountView()
    if (flow === 'signup') {
      sdk.requestSignupCode.mockResolvedValueOnce(apiError(message))
      await button(wrapper, 'Create account').trigger('click')
      await wrapper.get('#inviteCode').setValue('JUNTO-TEST')
    } else {
      sdk.requestCode.mockResolvedValueOnce(apiError(message))
    }
    await wrapper.get('#email').setValue('person@example.com')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe(message)
    expect(wrapper.find('label[for="code"]').exists()).toBe(false)
  })
})

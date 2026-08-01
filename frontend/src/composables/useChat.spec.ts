import { describe, expect, it } from 'vitest'
import { reconcileSavedMessage, type ChatMessage } from './useChat'

function message(id: string, content = 'hello'): ChatMessage {
  return {
    id,
    activityId: 'activity-1',
    userId: 'user-1',
    displayName: 'Ada',
    avatarUrl: null,
    content,
    type: 'USER',
    createdAt: '2026-08-01T12:00:00Z',
  }
}

describe('reconcileSavedMessage', () => {
  it('collapses an optimistic row and an earlier websocket echo to one saved message', () => {
    const saved = message('saved-1')
    const result = reconcileSavedMessage(
      [message('older'), message('temp-1'), saved],
      'temp-1',
      saved,
    )

    expect(result.map(item => item.id)).toEqual(['older', 'saved-1'])
  })

  it('does not duplicate a websocket echo that arrives after the REST response', () => {
    const saved = message('saved-1')
    const reconciled = reconcileSavedMessage([message('temp-1')], 'temp-1', saved)
    const afterEcho = reconciled.some(item => item.id === saved.id)
      ? reconciled
      : [...reconciled, saved]

    expect(afterEcho).toEqual([saved])
  })
})

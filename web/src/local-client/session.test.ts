import { beforeEach, describe, expect, it } from 'vitest'
import { readSessionToken } from './session'

describe('readSessionToken', () => {
  beforeEach(() => {
    sessionStorage.clear()
    history.replaceState(null, '', '/ui/')
  })

  it('reads a token from the fragment, stores it, and removes the fragment', () => {
    history.replaceState(null, '', '/ui/#token=local-token')

    expect(readSessionToken(window.location, sessionStorage)).toBe('local-token')
    expect(sessionStorage.getItem('droidscope.session')).toBe('local-token')
    expect(window.location.hash).toBe('')
  })

  it('restores a token from session storage after refresh', () => {
    sessionStorage.setItem('droidscope.session', 'stored-token')

    expect(readSessionToken(window.location, sessionStorage)).toBe('stored-token')
  })
})

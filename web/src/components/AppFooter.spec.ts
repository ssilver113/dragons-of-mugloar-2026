import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AppFooter from './AppFooter.vue'

function render(props: { version?: string | null; builtAt?: string | null } = {}) {
  return mount(AppFooter, { props: { version: null, builtAt: null, ...props } })
}

describe('AppFooter', () => {
  it('names the build that answered', () => {
    expect(render({ version: '0.9' }).text()).toContain('Dragons of Mugloar v0.9')
  })

  it('dates the build in one fixed format, whoever is reading', () => {
    expect(render({ version: '0.9', builtAt: '2026-09-10T09:15:00Z' }).text()).toContain(
      '10 September 2026',
    )
  })

  /** The printed date is for a person; the attribute is for anything else that reads the page. */
  it('leaves the instant itself on the element', () => {
    const footer = render({ version: '0.9', builtAt: '2026-09-10T09:15:00Z' })

    expect(footer.get('time').attributes('datetime')).toBe('2026-09-10T09:15:00Z')
  })

  /**
   * A jar built without the build-info task still serves a game. The name is the landmark and
   * stays; the stamp is the part that has nothing to say.
   */
  it('says only the name when the server carries no build information', () => {
    const footer = render({ version: null, builtAt: null })

    expect(footer.text()).toBe('Dragons of Mugloar')
    expect(footer.find('time').exists()).toBe(false)
  })

  it('drops an instant it cannot read rather than printing what Date made of it', () => {
    const footer = render({ version: '0.9', builtAt: 'not a date' })

    expect(footer.text()).not.toContain('Invalid Date')
    expect(footer.find('time').exists()).toBe(false)
  })

  it('is the page landmark for everything that is about the software, not the game', () => {
    expect(render({ version: '0.9' }).find('footer').exists()).toBe(true)
  })
})

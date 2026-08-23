import { expect, type Locator, type Page } from '@playwright/test'
import { FakeApi, type FakeApiOptions } from './fake-api'

/**
 * Reading the app the way a player does — by what is on screen, not by what is in a store. Every
 * locator here goes through an accessible name, so a spec that passes is also evidence the
 * control it drove is reachable without a mouse.
 */

/** One figure from the status strip, found by its label rather than its position. */
export function stat(page: Page, label: string): Locator {
  return page
    .locator('dl[aria-label="Dragon status"] > div')
    .filter({ has: page.getByText(label, { exact: true }) })
    .locator('dd')
}

export function board(page: Page): Locator {
  return page.getByRole('region', { name: 'Message board' })
}

/** Every job currently offered, as the buttons that would take it. */
export function jobs(page: Page): Locator {
  return board(page).getByRole('button', { name: /^Solve: / })
}

/**
 * The solver's controls, which the app keeps folded away. A player arriving at the game is there
 * to play it, so the panel that hands it over starts shut and every spec that drives the solver
 * has to open it first — exactly as a player would.
 */
export async function openAutoPlay(page: Page): Promise<void> {
  const step = page.getByRole('button', { name: 'Step' })
  if (!(await step.isVisible())) {
    await page.locator('summary', { hasText: 'Auto-play' }).click()
  }
  await expect(step).toBeVisible()
}

/** Serve the API from inside the page, open the app and start a game. */
export async function startGame(page: Page, options: FakeApiOptions = {}): Promise<FakeApi> {
  const api = new FakeApi(options)
  await api.install(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Start a game' }).click()
  await expect(stat(page, 'Turn')).toHaveText('0')
  return api
}

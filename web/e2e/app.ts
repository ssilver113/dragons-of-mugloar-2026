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
 * The solver's controls. Nothing has to be opened any more — the drive sits with the log it fills,
 * below the board and the shop, and is on screen from the moment the game starts. The wait is what
 * is left of the helper, and it is worth keeping: every spec that drives the solver needs the
 * machine mounted before it presses anything.
 *
 * On a narrow window the drive is behind the third switch instead. These specs run at the default
 * viewport, which is wide enough that all three columns are up at once.
 */
export async function openAutoPlay(page: Page): Promise<void> {
  await expect(page.getByRole('button', { name: 'Step' })).toBeVisible()
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

import { expect, test } from '@playwright/test'
import { jobs, startGame, stat } from './app'
import { FakeApi } from './fake-api'

test('an upstream failure is reported, and the way out is a refetch', async ({ page }) => {
  const api = new FakeApi()
  await api.install(page)
  // The game starts; only the board behind it does not arrive.
  api.failNext(/\/ads$/, {
    status: 502,
    code: 'UPSTREAM_UNAVAILABLE',
    detail: 'The game service did not answer in time.',
  })
  await page.goto('/')

  await page.getByRole('button', { name: 'Start a game' }).click()

  const alert = page.getByRole('alert').filter({ hasText: 'The game service is down' })
  await expect(alert).toBeVisible()
  await expect(alert).toContainText('The game service did not answer in time.')
  await expect(page.getByText('The message board could not be loaded.')).toBeVisible()

  // Never a retry of the action that failed — a board fetch, which costs no turn upstream.
  await alert.getByRole('button', { name: 'Refresh the board' }).click()

  await expect(jobs(page)).toHaveCount(6)
  await expect(stat(page, 'Turn')).toHaveText('0')
})

test('a session the server has forgotten ends the run without calling it a defeat', async ({
  page,
}) => {
  const api = await startGame(page)
  api.failNext(/\/solve$/, {
    status: 404,
    code: 'SESSION_EXPIRED',
    detail: 'This game is no longer being tracked.',
  })

  await page
    .getByRole('button', { name: 'Solve: Escort a merchant caravan to Ravenhold (job 1)' })
    .click()

  await expect(page.getByRole('heading', { name: 'This game was lost' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'The dragon has fallen' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Start a new game' })).toBeVisible()
  // The optimistic turn is rolled back: the last state the server confirmed is what stands.
  await expect(page.getByText('It was worth 0 points after 0 turns.')).toBeVisible()
})

test('the app can be played at 375px, where the board and shop take turns', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await startGame(page)

  await expect(jobs(page)).toHaveCount(6)
  await expect(page.getByRole('heading', { name: 'Shop' })).toBeHidden()

  await page.getByRole('button', { name: 'Shop', exact: true }).click()

  await expect(page.getByRole('heading', { name: 'Shop' })).toBeVisible()
  await expect(jobs(page).first()).toBeHidden()
  // Nothing may push the page sideways at the narrowest supported width.
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  )
  expect(overflow).toBeLessThanOrEqual(0)
})

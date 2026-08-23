import { expect, test } from '@playwright/test'
import { board, jobs, openAutoPlay, startGame, stat } from './app'

test('starting a game deals a board and the status strip reads zero', async ({ page }) => {
  await startGame(page)

  await expect(page.getByRole('heading', { name: 'Dragons of Mugloar' })).toBeVisible()
  await expect(jobs(page)).toHaveCount(6)
  await expect(stat(page, 'Score')).toHaveText('0')
  await expect(stat(page, 'Lives')).toHaveText('3')
})

test('solving a job moves the score, spends a turn and takes the job off the board', async ({
  page,
}) => {
  await startGame(page)
  const job = 'Escort a merchant caravan to Ravenhold (job 1)'

  await page.getByRole('button', { name: `Solve: ${job}` }).click()

  await expect(page.getByText('Mission accomplished')).toBeVisible()
  await expect(stat(page, 'Score')).toHaveText('42')
  await expect(stat(page, 'Gold')).toHaveText('42')
  await expect(stat(page, 'Turn')).toHaveText('1')
  // The board is refilled, so what proves the job was taken is its absence, not a shorter list.
  await expect(board(page).getByText(job)).toHaveCount(0)
  await expect(jobs(page)).toHaveCount(6)
})

test('a failed job costs a life and says so', async ({ page }) => {
  await startGame(page)

  await page
    .getByRole('button', { name: 'Solve: Steal the crown jewels from the state vault (job 5)' })
    .click()

  await expect(page.getByText('Mission failed')).toBeVisible()
  await expect(stat(page, 'Lives')).toHaveText('2')
  await expect(stat(page, 'Score')).toHaveText('0')
})

/**
 * `hidden lg:block` and `flex` are both display utilities, and the variant is emitted later, so
 * putting them on one element leaves a column laid out as blocks with its gap doing nothing. It is
 * invisible to every unit test and to the 375px check, because it only appears from `lg` up.
 */
test('the shop column is still a spaced stack once the layout goes wide', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await startGame(page)

  const column = page.locator('div.min-w-0 > div.flex.flex-col.gap-4').last()
  const gap = await column.evaluate((el) => {
    const [shop, standing] = [...el.children].map((child) => child.getBoundingClientRect())
    return {
      display: getComputedStyle(el).display,
      between: Math.round(standing.top - shop.bottom),
    }
  })

  expect(gap.display).toBe('flex')
  expect(gap.between).toBeGreaterThan(0)
})

test('the advisor is opt-in, and turning it on annotates every job', async ({ page }) => {
  await startGame(page)

  await expect(page.getByText("Advisor's read")).toHaveCount(0)
  await page.getByRole('switch', { name: 'Advisor' }).click()

  await expect(page.getByText("Advisor's read")).toHaveCount(6)
  await expect(board(page).getByText('Trap').first()).toBeVisible()
})

/**
 * Tailwind v4's preflight sets `cursor: default` on buttons, which v3 did not, so every control in
 * the app silently stopped claiming to be one. The fix is a base-layer rule and this is what keeps
 * it: a computed style is the only thing that would have caught the regression in the first place.
 */
test('every control points at itself, and a disabled one does not', async ({ page }) => {
  await startGame(page)
  await openAutoPlay(page)

  const cursor = (selector: string) =>
    page
      .locator(selector)
      .first()
      .evaluate((el) => getComputedStyle(el).cursor)

  expect(await cursor('button:not(:disabled)')).toBe('pointer')
  expect(await cursor('select')).toBe('pointer')
  expect(await cursor('summary')).toBe('pointer')
  expect(await cursor('[role="switch"]')).toBe('pointer')
  expect(await cursor('button:disabled')).toBe('not-allowed')

  // The filter checkboxes and the posture radios only exist once the advisor is on.
  await page.getByRole('switch', { name: 'Advisor' }).click()
  expect(await cursor('input[type="checkbox"]')).toBe('pointer')
  expect(await cursor('label:has(input[type="checkbox"])')).toBe('pointer')
  expect(await cursor('label:has(input[type="radio"])')).toBe('pointer')
})

test('a reload picks the game back up rather than losing it', async ({ page }) => {
  await startGame(page)
  await page
    .getByRole('button', { name: 'Solve: Escort a merchant caravan to Ravenhold (job 1)' })
    .click()
  await expect(stat(page, 'Turn')).toHaveText('1')

  await page.reload()

  // No click on Start a game: the id outlived the page, and the board and shop are free to fetch.
  await expect(stat(page, 'Turn')).toHaveText('1')
  await expect(stat(page, 'Score')).toHaveText('42')
  await expect(jobs(page)).toHaveCount(6)
  await expect(page.getByRole('button', { name: 'Start a game' })).toHaveCount(0)
})

test('a run is abandoned from the bottom of the page, and never by one click', async ({ page }) => {
  await startGame(page)
  await page
    .getByRole('button', { name: 'Solve: Escort a merchant caravan to Ravenhold (job 1)' })
    .click()
  await expect(stat(page, 'Turn')).toHaveText('1')

  await page.getByRole('button', { name: 'Start a new game' }).click()
  await page.getByRole('button', { name: 'Keep playing' }).click()

  await expect(stat(page, 'Turn')).toHaveText('1')

  await page.getByRole('button', { name: 'Start a new game' }).click()
  await page.getByRole('button', { name: 'Yes, start a new game' }).click()

  await expect(stat(page, 'Turn')).toHaveText('0')
  await expect(stat(page, 'Score')).toHaveText('0')
  await expect(jobs(page)).toHaveCount(6)
})

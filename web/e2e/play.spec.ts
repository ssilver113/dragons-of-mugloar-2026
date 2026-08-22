import { expect, test } from '@playwright/test'
import { board, jobs, startGame, stat } from './app'

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
    return { display: getComputedStyle(el).display, between: Math.round(standing.top - shop.bottom) }
  })

  expect(gap.display).toBe('flex')
  expect(gap.between).toBeGreaterThan(0)
})

test('the advisor is opt-in, and turning it on annotates every job', async ({ page }) => {
  await startGame(page)

  await expect(page.getByText("Advisor's read")).toHaveCount(0)
  await page.getByRole('checkbox', { name: 'Advisor' }).check()

  await expect(page.getByText("Advisor's read")).toHaveCount(6)
  await expect(board(page).getByText('Trap').first()).toBeVisible()
})

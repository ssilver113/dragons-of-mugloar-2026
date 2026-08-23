import { expect, test } from '@playwright/test'
import { openAutoPlay, startGame, stat } from './app'

test('one solver turn is logged with the reasoning behind it', async ({ page }) => {
  await startGame(page)
  await openAutoPlay(page)

  await page.getByRole('button', { name: 'Step' }).click()

  await expect(stat(page, 'Turn')).toHaveText('1')
  await expect(stat(page, 'Score')).toHaveText('42')
  const log = page.getByRole('region', { name: 'Decision log' })
  await expect(log.getByText('1 turn, newest first')).toBeVisible()
  await expect(log.getByText('Escort a merchant caravan to Ravenhold (job 1)').first()).toBeVisible()
})

test('auto-play runs the game to its end and the log outlives the board', async ({ page }) => {
  // Two lives and a scripted run: two jobs land, then two go wrong, which is exactly a dead dragon.
  await startGame(page, { lives: 2, autoPlay: [true, true, false, false] })
  await openAutoPlay(page)

  await page.getByLabel('Speed').selectOption('max')
  await page.getByRole('button', { name: 'Run' }).click()

  const ending = page.getByRole('heading', { name: 'The dragon has fallen' })
  await expect(ending).toBeVisible()
  await expect(page.getByText('Final score 84 points after 4 turns.')).toBeVisible()

  // The board is gone, but the run is still worth reading.
  await expect(page.getByRole('button', { name: /^Solve: / })).toHaveCount(0)
  await expect(page.getByText('4 turns, newest first')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Play again' })).toBeVisible()
})

test('a run can be stopped between turns', async ({ page }) => {
  await startGame(page)
  await openAutoPlay(page)

  // Slow, so the pause lands mid-run rather than after a game that has already finished.
  await page.getByLabel('Speed').selectOption('slow')
  await page.getByRole('button', { name: 'Run' }).click()
  await expect(page.getByText('Running. The solver is taking every turn.')).toBeVisible()

  await page.getByRole('button', { name: 'Pause' }).click()

  await expect(
    page.getByText('Idle. The solver takes a turn only when you ask it to.'),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: 'Run' })).toBeEnabled()
})

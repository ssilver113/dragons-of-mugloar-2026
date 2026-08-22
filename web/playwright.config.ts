import { defineConfig, devices } from '@playwright/test'

const PORT = 4173

/**
 * The suite runs against the *built* bundle rather than the dev server. The build target is
 * pinned to the browsers the README claims, so what a reviewer runs is the compiled output —
 * testing the dev server's untranspiled modules would exercise code that never ships.
 *
 * The backend is stubbed in the page (see `e2e/fake-api.ts`), so this needs neither Java nor the
 * live game API, and a CI run cannot fail because dragonsofmugloar.com is having a bad morning.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: 'on-first-retry',
  },
  // The cross-browser claim is checked here, on every run, rather than asserted in a README.
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
  webServer: {
    command: `npm run build && npm run preview -- --port ${PORT} --strictPort`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})

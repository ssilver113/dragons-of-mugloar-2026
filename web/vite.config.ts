/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// The browser never calls the Mugloar API directly; /api is our own
// backend, dev-proxied so the app runs same-origin in development.
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  // Pinned rather than left on Vite's rolling default, so the browsers the README claims are the
  // browsers the bundle is actually compiled for. The floor is Tailwind v4's, not Vite's: the
  // generated CSS uses @property and color-mix(), which nothing older understands whatever the
  // JavaScript is transpiled to.
  build: {
    target: ['chrome111', 'edge111', 'firefox128', 'safari16.4'],
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.API_URL ?? 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    // Unit specs only. The Playwright suite in e2e/ matches the same *.spec.ts glob and would
    // otherwise be collected here, where there is no browser to run it in.
    include: ['src/**/*.spec.ts'],
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
    },
  },
})

import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

/**
 * Flat config, and deliberately a small one.
 *
 * `vue-tsc` already runs in strict mode over `src/` and `e2e/`, so type errors are caught by the
 * build rather than by a second type-aware pass here; what this adds is the idiom the compiler has
 * no opinion about. Formatting is Prettier's, so `skip-formatting` turns off every stylistic rule
 * that would otherwise disagree with it — the two tools are not allowed to have opinions about the
 * same thing.
 */
export default defineConfigWithVueTs(
  {
    name: 'app/ignores',
    // Build output and reports. `node_modules` is ignored by default.
    ignores: ['dist/**', 'coverage/**', 'playwright-report/**', 'test-results/**'],
  },

  js.configs.recommended,
  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,

  {
    name: 'app/rules',
    files: ['**/*.{ts,vue}'],
    rules: {
      // The codebase marks a deliberately unawaited promise with `void`; without this the marker
      // is decoration. Type-aware, so it is stated here rather than pulled in with a whole preset.
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', caughtErrors: 'none' },
      ],
      // Single-word component names are fine for `App`; everything else here is already compound.
      'vue/multi-word-component-names': ['error', { ignores: ['App'] }],
    },
  },

  {
    name: 'app/specs',
    files: ['**/*.spec.ts'],
    rules: {
      // A spec asserting on a hostile-looking payload has to be able to describe one.
      '@typescript-eslint/no-explicit-any': 'off',
    },
  },

  skipFormatting,
)

import { defineConfig } from 'vitest/config';

/**
 * Merged into the config the Angular unit-test builder generates.
 * The explicit jsdom URL is what makes `window.localStorage` available —
 * without an origin jsdom refuses storage access.
 */
export default defineConfig({
  test: {
    environment: 'jsdom',
    environmentOptions: {
      jsdom: { url: 'http://localhost/' },
    },
  },
});

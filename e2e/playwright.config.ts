import { defineConfig, devices } from '@playwright/test';

// Variables d'env optionnelles : E2E_BASE_URL, ADMIN_USERNAME, ADMIN_PASSWORD (voir tests/support/api.ts).
export default defineConfig({
  testDir: './tests',
  globalSetup: require.resolve('./tests/support/global-setup'),
  timeout: 30000,
  expect: { timeout: 10000 },
  // Serie totale (fullyParallel:false) : les tests login partagent la meme limite nginx (5r/m sur
  // /api/auth/login) - paralleliser risquerait des 429 qui ne seraient pas de vrais bugs.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'https://localhost:8443',
    ignoreHTTPSErrors: true,
    trace: 'retain-on-failure',
    actionTimeout: 15000,
    navigationTimeout: 30000,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});

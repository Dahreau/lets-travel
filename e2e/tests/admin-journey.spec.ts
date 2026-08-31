import { expect, Page, test } from '@playwright/test';
import { readFixture } from './support/fixture';

test.describe.serial('Parcours Admin', () => {
  let page: Page;

  // Injecte le token de global-setup.ts au lieu de se reconnecter via l'UI (login rate-limite,
  // deja teste par auth.spec.ts) - voir troubleshooting.md #53.
  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    const fixture = readFixture();
    await page.goto('/login');
    await page.evaluate((token) => localStorage.setItem('travel-plan.admin.token', token), fixture.adminToken);
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test.afterAll(async () => {
    await page.close();
  });

  // dashboard.ts charge tout (users/travels/payments/rankings + /api/reports) dans UN SEUL forkJoin :
  // si /api/reports echoue, tout le dashboard reste vide - ce test verifie ce risque connu.
  test('le dashboard admin affiche les compteurs globaux et les classements', async () => {
    await expect(page.locator('.stat-card', { hasText: 'users' }).locator('.stat-value')).not.toHaveText('0', {
      timeout: 15000,
    });
    await expect(page.locator('h2.section-title', { hasText: 'top managers' })).toBeVisible();
  });

  test('la liste des utilisateurs est accessible et montre le manager de fixture', async () => {
    const fixture = readFixture();
    await page.goto('/users');
    // Le nom affiche ("E2E Manager") se repete a chaque run precedent jamais nettoye - seul le
    // username (unique, horodate) identifie sans ambiguite CE manager-la.
    await expect(page.getByText(fixture.managerUsername).first()).toBeVisible();
  });

  test('la liste des voyages est accessible et montre le voyage de fixture', async () => {
    const fixture = readFixture();
    await page.goto('/travels');
    await expect(page.getByText(fixture.travelTitle)).toBeVisible();
  });
});

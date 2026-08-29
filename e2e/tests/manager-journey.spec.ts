import { expect, Page, test } from '@playwright/test';
import { readFixture } from './support/fixture';

test.describe.serial('Parcours Travel Manager', () => {
  let page: Page;

  // Injecte le token deja obtenu par global-setup.ts au lieu de se reconnecter via l'UI - le
  // login est rate-limite cote nginx, et il est deja teste par auth.spec.ts (voir #53).
  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    const fixture = readFixture();
    await page.goto('/login');
    await page.evaluate((token) => localStorage.setItem('travel-plan.admin.token', token), fixture.managerToken);
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test.afterAll(async () => {
    await page.close();
  });

  test('voit son voyage de fixture sur son dashboard', async () => {
    await expect(page.locator('h2.section-title', { hasText: 'mes voyages' })).toBeVisible();
    const fixture = readFixture();
    await expect(page.getByText(fixture.travelTitle)).toBeVisible();
  });

  test('cree un nouveau voyage via le formulaire', async () => {
    const title = `E2E Manager Travel ${Date.now()}`;

    await page.goto('/travels/new');
    await page.fill('#title', title);
    await page.fill('#startDate', '2027-03-01');
    await page.fill('#endDate', '2027-03-10');
    await page.selectOption('#status', 'CONFIRMED');
    await page.fill('#price', '250');
    await page.fill('#currency', 'EUR');

    // Le formulaire demarre deja avec une destination (ngOnInit appelle addDestination() une
    // fois) - cliquer "+ add destination" ici en ajoutait une 2e, vide et obligatoire, qui
    // bloquait la soumission (validation Angular) sans jamais declencher le POST attendu.
    const destination = page.locator('.subcard').first();
    await destination.locator('input[formcontrolname="city"]').fill('Lyon');
    await destination.locator('input[formcontrolname="country"]').fill('France');
    await destination.locator('input[formcontrolname="arrivalDate"]').fill('2027-03-01');
    await destination.locator('input[formcontrolname="departureDate"]').fill('2027-03-10');

    const [createRes] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/travels') && r.request().method() === 'POST'),
      page.click('button:has-text("$ save")'),
    ]);
    expect(createRes.status()).toBe(201);
    await expect(page).toHaveURL(/\/travels$/);
    await expect(page.getByText(title)).toBeVisible();
  });

  test('consulte les abonnes et le feedback du voyage de fixture', async () => {
    const fixture = readFixture();
    await page.goto(`/manager/travels/${fixture.travelId}`);
    await expect(page.locator('h2.section-title', { hasText: 'abonnés' })).toBeVisible();
    await expect(page.locator('h2.section-title', { hasText: 'feedback' })).toBeVisible();
  });
});

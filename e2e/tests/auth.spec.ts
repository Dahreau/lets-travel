import { expect, test } from '@playwright/test';
import { readFixture } from './support/fixture';

test.describe('Authentification', () => {
  test("un traveler peut s'inscrire via le formulaire public et atterrit sur le dashboard", async ({
    page,
  }) => {
    const suffix = Date.now();
    const username = `e2e-auth-${suffix}`;

    await page.goto('/register');
    await page.fill('#firstName', 'E2E');
    await page.fill('#lastName', 'AuthTest');
    await page.fill('#email', `${username}@example.com`);
    await page.fill('#username', username);
    await page.fill('#password', 'E2e-test-pass-1!');
    await page.check('#acceptedPrivacyPolicy');

    const [registerRes] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/auth/register') && r.request().method() === 'POST'),
      page.click('button[type="submit"]'),
    ]);
    expect(registerRes.status()).toBe(201);

    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.locator('.topbar-user .user-name')).toContainText(username);
  });

  test('un manager existant peut se connecter puis se deconnecter', async ({ page }) => {
    const fixture = readFixture();

    await page.goto('/login');
    await page.fill('#username', fixture.managerUsername);
    await page.fill('#password', fixture.managerPassword);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/dashboard$/);

    await page.click('button:has-text("logout")');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('un mot de passe invalide affiche une erreur sans quitter /login', async ({ page }) => {
    const fixture = readFixture();

    await page.goto('/login');
    await page.fill('#username', fixture.managerUsername);
    await page.fill('#password', 'mauvais-mot-de-passe');
    await page.click('button[type="submit"]');

    await expect(page.locator('.login-error')).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });
});

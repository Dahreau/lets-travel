import { expect, Page, test } from '@playwright/test';
import { readFixture } from './support/fixture';

// Une seule inscription pour toute la serie : les tests suivants reutilisent la meme page/session
// (meme traveler) plutot que de re-authentifier a chaque test.
test.describe.serial('Parcours Traveler', () => {
  let page: Page;
  const suffix = Date.now();
  const username = `e2e-journey-${suffix}`;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
  });

  test.afterAll(async () => {
    await page.close();
  });

  test("s'inscrit via le formulaire public", async () => {
    await page.goto('/register');
    await page.fill('#firstName', 'E2E');
    await page.fill('#lastName', 'Journey');
    await page.fill('#email', `${username}@example.com`);
    await page.fill('#username', username);
    await page.fill('#password', 'E2e-test-pass-1!');
    await page.check('#acceptedPrivacyPolicy');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test('trouve le voyage de fixture via la recherche /browse', async () => {
    const fixture = readFixture();
    await page.goto('/browse');
    await page.fill('#q', fixture.travelTitle);
    await page.click('button:has-text("chercher")');

    const row = page.locator('table.table tbody tr', { hasText: fixture.travelTitle });
    await expect(row).toBeVisible();
  });

  test("s'abonne au voyage puis le retrouve sur sa fiche detail", async () => {
    const fixture = readFixture();
    await page.goto(`/browse/${fixture.travelId}`);

    const [subscribeRes] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/subscriptions') && r.request().method() === 'POST'),
      page.click("button:has-text(\"s'abonner\")"),
    ]);
    expect(subscribeRes.status()).toBe(201);
    await expect(page.getByText('Vous êtes abonné à ce voyage.')).toBeVisible();
  });

  test('ajoute un moyen de paiement puis paie le voyage de fixture', async () => {
    await page.goto('/payment-methods/new');
    await page.selectOption('#provider', 'STRIPE');
    await page.selectOption('#type', 'CARD');
    await page.fill('#providerToken', 'pm_card_visa');
    await page.fill('#brand', 'Visa');
    await page.fill('#last4', '4242');

    const [paymentMethodRes] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/payment-methods') && r.request().method() === 'POST'),
      page.click('button:has-text("$ save")'),
    ]);
    expect(paymentMethodRes.status()).toBe(201);
    await expect(page).toHaveURL(/\/payment-methods$/);

    const fixture = readFixture();
    await page.goto(`/browse/${fixture.travelId}`);
    await page.selectOption('#paymentMethodId', { index: 1 });

    const [paymentRes] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/payments') && r.request().method() === 'POST'),
      page.click('button:has-text("$ payer")'),
    ]);
    // Le montant vient de travel-service (voir PaymentRequest), passe par un vrai Stripe en
    // mode test : on verifie le code HTTP reel plutot qu'un etat visuel de succes non garanti.
    expect(paymentRes.status()).toBe(201);
  });

  test('se desabonne du voyage', async () => {
    const fixture = readFixture();
    await page.goto(`/browse/${fixture.travelId}`);

    const [unsubscribeRes] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/subscriptions/') && r.request().method() === 'DELETE'),
      page.click("button:has-text('se désabonner')"),
    ]);
    expect(unsubscribeRes.status()).toBe(204);
    await expect(page.getByText("$ s'abonner")).toBeVisible();
  });
});

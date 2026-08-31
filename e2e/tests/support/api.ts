import { APIRequestContext } from '@playwright/test';

export const BASE_URL = process.env.E2E_BASE_URL || 'https://localhost:8443';

export function isoDate(daysFromNow: number): string {
  return new Date(Date.now() + daysFromNow * 86400000).toISOString().slice(0, 10);
}

async function expectOk(res: { ok(): boolean; status(): number; url(): string }, action: string) {
  if (!res.ok()) {
    throw new Error(`${action} a echoue (HTTP ${res.status()}) sur ${res.url()}`);
  }
}

export async function apiLogin(
  request: APIRequestContext,
  username: string,
  password: string,
): Promise<string> {
  const res = await request.post(`${BASE_URL}/api/auth/login`, { data: { username, password } });
  await expectOk(res, `Login (${username})`);
  return (await res.json()).token as string;
}

// Inscription publique (2 appels, comme le flux UI reel) - n'utilise jamais /api/auth/login,
// donc jamais soumise a sa limite de debit (voir infra/nginx/nginx-main.conf).
export async function apiRegisterTraveler(
  request: APIRequestContext,
  suffix: string | number,
): Promise<{ token: string; userId: string; username: string }> {
  const username = `e2e-traveler-${suffix}`;
  const registerRes = await request.post(`${BASE_URL}/api/users/register`, {
    data: {
      firstName: 'E2E',
      lastName: 'Traveler',
      email: `${username}@example.com`,
      phone: '0600000001',
      acceptedPrivacyPolicy: true,
    },
  });
  await expectOk(registerRes, 'Inscription traveler (profil)');
  const registerBody = await registerRes.json();

  const authRes = await request.post(`${BASE_URL}/api/auth/register`, {
    data: {
      username,
      password: 'E2e-test-pass-1!',
      registrationToken: registerBody.registrationToken,
    },
  });
  await expectOk(authRes, 'Inscription traveler (compte)');
  const authBody = await authRes.json();
  return { token: authBody.token as string, userId: registerBody.user.id as string, username };
}

export async function apiCreateManager(
  request: APIRequestContext,
  adminToken: string,
  suffix: string | number,
): Promise<{ managerId: string; username: string; password: string }> {
  const username = `e2e-manager-${suffix}`;
  const password = 'E2e-test-pass-1!';

  const userRes = await request.post(`${BASE_URL}/api/users`, {
    data: {
      firstName: 'E2E',
      lastName: 'Manager',
      email: `${username}@example.com`,
      phone: '0600000000',
      role: 'TRAVEL_MANAGER',
      address: { street: '1 rue du Test', city: 'Paris', postalCode: '75001', country: 'France' },
    },
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  await expectOk(userRes, 'Creation profil manager');
  const managerId = (await userRes.json()).id as string;

  const accountRes = await request.post(`${BASE_URL}/api/auth/accounts`, {
    data: { username, password, role: 'TRAVEL_MANAGER', userId: managerId },
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  await expectOk(accountRes, 'Creation compte manager');

  return { managerId, username, password };
}

export async function apiCreateTravel(
  request: APIRequestContext,
  managerToken: string,
  opts: { title: string; startDaysFromNow: number; endDaysFromNow: number; status: string; price: number },
): Promise<{ id: string; title: string }> {
  const res = await request.post(`${BASE_URL}/api/travels`, {
    data: {
      title: opts.title,
      startDate: isoDate(opts.startDaysFromNow),
      endDate: isoDate(opts.endDaysFromNow),
      status: opts.status,
      price: opts.price,
      currency: 'EUR',
      destinations: [
        {
          city: 'Paris',
          country: 'France',
          arrivalDate: isoDate(opts.startDaysFromNow),
          departureDate: isoDate(opts.endDaysFromNow),
          orderIndex: 0,
        },
      ],
    },
    headers: { Authorization: `Bearer ${managerToken}` },
  });
  await expectOk(res, 'Creation voyage');
  const body = await res.json();
  return { id: body.id as string, title: body.title as string };
}

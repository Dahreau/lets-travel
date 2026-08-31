import { mkdirSync, writeFileSync } from 'fs';
import path from 'path';
import { request as pwRequest } from '@playwright/test';
import { apiCreateManager, apiCreateTravel, apiLogin, BASE_URL } from './api';
import { FIXTURE_PATH } from './fixture';

const ADMIN_USERNAME = process.env.ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'changeme_dev_only';

// S'execute une fois avant toute la suite : prepare un manager + un voyage reels via l'API,
// pour eviter de recreer ces prealables ou multiplier les appels /api/auth/login (rate-limite).
export default async function globalSetup(): Promise<void> {
  const runId = Date.now();
  const api = await pwRequest.newContext({ baseURL: BASE_URL, ignoreHTTPSErrors: true });

  try {
    const adminToken = await apiLogin(api, ADMIN_USERNAME, ADMIN_PASSWORD);
    const manager = await apiCreateManager(api, adminToken, runId);
    const managerToken = await apiLogin(api, manager.username, manager.password);
    const travel = await apiCreateTravel(api, managerToken, {
      title: `E2E Voyage fixture ${runId}`,
      startDaysFromNow: 30,
      endDaysFromNow: 35,
      status: 'CONFIRMED',
      price: 499.5,
    });

    mkdirSync(path.dirname(FIXTURE_PATH), { recursive: true });
    writeFileSync(
      FIXTURE_PATH,
      JSON.stringify(
        {
          runId,
          adminUsername: ADMIN_USERNAME,
          adminPassword: ADMIN_PASSWORD,
          adminToken,
          managerUsername: manager.username,
          managerPassword: manager.password,
          managerToken,
          managerId: manager.managerId,
          travelId: travel.id,
          travelTitle: travel.title,
        },
        null,
        2,
      ),
    );
  } finally {
    await api.dispose();
  }
}

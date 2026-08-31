import { readFileSync } from 'fs';
import path from 'path';

export const FIXTURE_PATH = path.join(__dirname, '..', '..', '.fixtures', 'run.json');

export interface RunFixture {
  runId: number;
  adminUsername: string;
  adminPassword: string;
  adminToken: string;
  managerUsername: string;
  managerPassword: string;
  managerToken: string;
  managerId: string;
  travelId: string;
  travelTitle: string;
}

export function readFixture(): RunFixture {
  return JSON.parse(readFileSync(FIXTURE_PATH, 'utf-8')) as RunFixture;
}

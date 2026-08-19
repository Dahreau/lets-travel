import { decodeJwtPayload, isTokenExpired } from './jwt-util';

function makeToken(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

describe('jwt-util', () => {
  describe('decodeJwtPayload', () => {
    it('decodes a valid JWT payload', () => {
      const token = makeToken({ sub: 'admin', exp: 123 });
      expect(decodeJwtPayload(token)).toEqual({ sub: 'admin', exp: 123 });
    });

    it('returns null for a malformed token', () => {
      expect(decodeJwtPayload('not-a-jwt')).toBeNull();
    });
  });

  describe('isTokenExpired', () => {
    it('returns false for a token expiring in the future', () => {
      const futureExp = Math.floor(Date.now() / 1000) + 3600;
      expect(isTokenExpired(makeToken({ exp: futureExp }))).toBe(false);
    });

    it('returns true for a token that already expired', () => {
      const pastExp = Math.floor(Date.now() / 1000) - 3600;
      expect(isTokenExpired(makeToken({ exp: pastExp }))).toBe(true);
    });

    it('returns true when the token has no exp claim', () => {
      expect(isTokenExpired(makeToken({ sub: 'admin' }))).toBe(true);
    });

    it('returns true for a malformed token', () => {
      expect(isTokenExpired('garbage')).toBe(true);
    });
  });
});

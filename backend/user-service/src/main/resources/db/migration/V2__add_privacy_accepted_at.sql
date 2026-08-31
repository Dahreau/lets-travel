-- fix/audit-gaps (troubleshooting.md #41) : trace le consentement donne a l'inscription
-- publique (case a cocher obligatoire cote frontend, voir UserRegistrationRequest.acceptedPrivacyPolicy).
-- Nullable : ne s'applique qu'au flux d'inscription publique (POST /api/users/register), pas aux
-- profils crees par un ADMIN via POST /api/users qui ne passent pas par ce consentement.
ALTER TABLE users ADD COLUMN privacy_accepted_at TIMESTAMP;

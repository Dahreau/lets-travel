-- L'entite "Admin" devient "Account" : porte les identifiants de connexion des
-- 3 roles (TRAVELER, TRAVEL_MANAGER, ADMIN), plus un lien optionnel vers le
-- profil User de user-service (null pour le compte ADMIN par defaut).
ALTER TABLE admins RENAME TO accounts;
ALTER TABLE accounts ADD COLUMN user_id UUID;

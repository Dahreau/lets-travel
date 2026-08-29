-- Un userId ne peut jamais etre lie a plus d'un compte. NULL (admin par defaut) reste
-- autorise plusieurs fois : Postgres ne considere jamais deux NULL comme egaux ici.
ALTER TABLE accounts ADD CONSTRAINT uq_accounts_user_id UNIQUE (user_id);

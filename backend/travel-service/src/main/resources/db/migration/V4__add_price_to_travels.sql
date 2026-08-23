-- Nullable : les voyages deja crees avant cette migration n'ont pas de prix retroactif.
-- Un Travel Manager doit modifier le voyage pour lui fixer un prix avant qu'il puisse etre
-- paye (payment-service refuse explicitement de facturer un voyage sans prix - voir
-- TravelPriceNotSetException). TravelRequest impose price/currency pour toute creation ou
-- modification a partir de maintenant.
ALTER TABLE travels ADD COLUMN price NUMERIC(10, 2);
ALTER TABLE travels ADD COLUMN currency VARCHAR(3);

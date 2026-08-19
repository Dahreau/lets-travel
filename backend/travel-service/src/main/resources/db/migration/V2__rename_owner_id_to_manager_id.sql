-- Un voyage est desormais une offre creee et geree par un Travel Manager
-- (managerId), plus la propriete d'un seul traveler. Les travelers s'y
-- abonneront via une entite Subscription a part (branche future).
ALTER TABLE travels RENAME COLUMN owner_id TO manager_id;

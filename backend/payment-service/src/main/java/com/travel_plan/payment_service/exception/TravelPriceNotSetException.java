package com.travel_plan.payment_service.exception;

import java.util.UUID;

// Levee quand le voyage existe mais n'a pas encore de prix (price/currency nuls, voyages crees
// avant migration V4) : pas de montant fiable a facturer tant qu'un prix n'est pas fixe.
public class TravelPriceNotSetException extends RuntimeException {

    public TravelPriceNotSetException(UUID travelId) {
        super("Le voyage " + travelId + " n'a pas encore de prix defini");
    }
}

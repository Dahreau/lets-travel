package com.travel_plan.payment_service.exception;

import java.util.UUID;

// Levee quand le voyage existe mais n'a pas encore de prix (price/currency nuls cote
// travel-service - voyages crees avant l'introduction du prix, voir travel-service
// migration V4). Pas de montant fiable a facturer tant qu'un Travel Manager n'a pas
// explicitement fixe un prix sur ce voyage.
public class TravelPriceNotSetException extends RuntimeException {

    public TravelPriceNotSetException(UUID travelId) {
        super("Travel " + travelId + " does not have a price set yet");
    }
}

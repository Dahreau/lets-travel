package com.travel_plan.travel_service.exception;

// Levee quand la requete est valide syntaxiquement mais viole une regle liee au role
// de l'appelant (ex. ADMIN qui cree un voyage sans preciser managerId).
public class InvalidTravelRequestException extends RuntimeException {

    public InvalidTravelRequestException(String message) {
        super(message);
    }
}

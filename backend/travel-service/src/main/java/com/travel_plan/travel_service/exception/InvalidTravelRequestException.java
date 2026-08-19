package com.travel_plan.travel_service.exception;

// Levee quand la requete est syntaxiquement valide (passe la Bean Validation)
// mais viole une regle qui depend du role de l'appelant - ex. un ADMIN qui
// cree un voyage sans preciser managerId.
public class InvalidTravelRequestException extends RuntimeException {

    public InvalidTravelRequestException(String message) {
        super(message);
    }
}

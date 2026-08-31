import { Component, input } from '@angular/core';
import { Destination } from '../../core/models/travel';

// Liste des destinations d'un voyage, partagee entre ManagerTravelDetail et TravelDetail
// (troubleshooting.md #77) - lecture seule, aucun formulaire implique.
@Component({
  selector: 'app-travel-destinations-list',
  templateUrl: './travel-destinations-list.html',
})
export class TravelDestinationsList {
  readonly destinations = input.required<Destination[]>();
}

import { Component, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

// Bloc Prenom/Nom/Email/Telephone partage entre Register et UserForm (troubleshooting.md #77) -
// prend les FormControl directement plutot qu'un FormGroup : les deux formulaires ont des
// formes differentes par ailleurs (username/password/RGPD vs role), seuls ces 4 champs matchent.
@Component({
  selector: 'app-name-contact-fields',
  imports: [ReactiveFormsModule],
  templateUrl: './name-contact-fields.html',
})
export class NameContactFields {
  readonly firstName = input.required<FormControl<string>>();
  readonly lastName = input.required<FormControl<string>>();
  readonly email = input.required<FormControl<string>>();
  readonly phone = input.required<FormControl<string>>();
}

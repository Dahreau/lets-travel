import { Component, input } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';

export interface AddressFormControls {
  street: FormControl<string>;
  city: FormControl<string>;
  postalCode: FormControl<string>;
  country: FormControl<string>;
}

// Sous-formulaire adresse partage entre Register et UserForm (troubleshooting.md #77) - le
// [formGroup] est pose sur un ng-container pour ne pas ajouter de div supplementaire, les deux
// pages gardent le controle total de leur propre wrapper (subcard, form-grid ou non).
@Component({
  selector: 'app-address-fields',
  imports: [ReactiveFormsModule],
  templateUrl: './address-fields.html',
})
export class AddressFields {
  readonly group = input.required<FormGroup<AddressFormControls>>();
}

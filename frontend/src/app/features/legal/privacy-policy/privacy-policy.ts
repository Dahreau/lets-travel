import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

// voir troubleshooting.md #41 - page publique, liee depuis l'inscription et "mon compte".
@Component({
  selector: 'app-privacy-policy',
  imports: [RouterLink],
  templateUrl: './privacy-policy.html',
})
export class PrivacyPolicy {}

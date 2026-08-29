import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

// fix/audit-gaps (troubleshooting.md #41) : page publique (voir app.routes.ts, meme groupe que
// /login et /register - pas de authGuard), liee depuis la case a cocher obligatoire de
// l'inscription (register.html) et depuis "mon compte" (my-data.html).
@Component({
  selector: 'app-privacy-policy',
  imports: [RouterLink],
  templateUrl: './privacy-policy.html',
})
export class PrivacyPolicy {}

import { Component, inject } from '@angular/core';
import { ToastService } from './toast';

@Component({
  selector: 'app-toast-outlet',
  templateUrl: './toast-outlet.html',
  styleUrl: './toast-outlet.scss',
})
export class ToastOutlet {
  protected readonly toastService = inject(ToastService);
}

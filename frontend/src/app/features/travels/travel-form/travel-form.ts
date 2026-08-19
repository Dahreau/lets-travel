import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { toDatetimeLocalValue, toIsoInstant } from '../../../core/util/datetime';
import { extractErrorMessage } from '../../../core/http/api-error';
import { ToastService } from '../../../core/notifications/toast';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { User } from '../../../core/models/user';
import {
  ACCOMMODATION_TYPES,
  Accommodation,
  Activity,
  Destination,
  TRANSPORTATION_TYPES,
  TRAVEL_STATUSES,
  Transportation,
  TravelRequest,
} from '../../../core/models/travel';
import { UsersService } from '../../users/users';
import { TravelsService } from '../travels';

@Component({
  selector: 'app-travel-form',
  imports: [ReactiveFormsModule, RouterLink, PageHeader, Spinner],
  templateUrl: './travel-form.html',
})
export class TravelForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly travelsService = inject(TravelsService);
  private readonly usersService = inject(UsersService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly statuses = TRAVEL_STATUSES;
  protected readonly accommodationTypes = ACCOMMODATION_TYPES;
  protected readonly transportationTypes = TRANSPORTATION_TYPES;

  protected readonly travelId = signal<string | null>(null);
  protected readonly isEdit = computed(() => this.travelId() !== null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly users = signal<User[]>([]);

  private createActivityGroup(activity?: Activity) {
    return this.fb.nonNullable.group({
      name: [activity?.name ?? '', Validators.required],
      description: [activity?.description ?? ''],
      date: [activity?.date ?? '', Validators.required],
      cost: this.fb.control<number | null>(activity?.cost ?? null),
    });
  }

  private createAccommodationGroup(accommodation?: Accommodation | null) {
    const group = this.fb.nonNullable.group({
      name: [accommodation?.name ?? '', Validators.required],
      type: [accommodation?.type ?? 'HOTEL', Validators.required],
      address: [accommodation?.address ?? '', Validators.required],
      checkIn: [accommodation?.checkIn ?? '', Validators.required],
      checkOut: [accommodation?.checkOut ?? '', Validators.required],
    });
    if (!accommodation) {
      group.disable();
    }
    return group;
  }

  private createDestinationGroup(destination?: Destination) {
    return this.fb.nonNullable.group({
      city: [destination?.city ?? '', Validators.required],
      country: [destination?.country ?? '', Validators.required],
      arrivalDate: [destination?.arrivalDate ?? '', Validators.required],
      departureDate: [destination?.departureDate ?? '', Validators.required],
      activities: this.fb.array(
        (destination?.activities ?? []).map((a) => this.createActivityGroup(a)),
      ),
      accommodation: this.createAccommodationGroup(destination?.accommodation ?? undefined),
    });
  }

  private createTransportationGroup(transportation?: Transportation) {
    return this.fb.nonNullable.group({
      type: [transportation?.type ?? 'FLIGHT', Validators.required],
      fromLocation: [transportation?.fromLocation ?? '', Validators.required],
      toLocation: [transportation?.toLocation ?? '', Validators.required],
      departureTime: [
        toDatetimeLocalValue(transportation?.departureTime),
        Validators.required,
      ],
      arrivalTime: [toDatetimeLocalValue(transportation?.arrivalTime), Validators.required],
      provider: [transportation?.provider ?? ''],
    });
  }

  protected readonly destinationsArray = this.fb.array<ReturnType<typeof this.createDestinationGroup>>(
    [],
  );
  protected readonly transportationsArray = this.fb.array<
    ReturnType<typeof this.createTransportationGroup>
  >([]);

  protected readonly form = this.fb.nonNullable.group({
    title: ['', Validators.required],
    ownerId: ['', Validators.required],
    startDate: ['', Validators.required],
    endDate: ['', Validators.required],
    status: ['PLANNED', Validators.required],
    destinations: this.destinationsArray,
    transportations: this.transportationsArray,
  });

  ngOnInit(): void {
    this.usersService.findAll().subscribe({
      next: (users) => this.users.set(users),
      error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.addDestination();
      return;
    }

    this.travelId.set(id);
    this.loading.set(true);
    this.travelsService
      .findById(id)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (travel) => {
          this.form.patchValue({
            title: travel.title,
            ownerId: travel.ownerId,
            startDate: travel.startDate,
            endDate: travel.endDate,
            status: travel.status,
          });
          this.destinationsArray.clear();
          travel.destinations.forEach((d) => this.destinationsArray.push(this.createDestinationGroup(d)));
          this.transportationsArray.clear();
          travel.transportations.forEach((t) =>
            this.transportationsArray.push(this.createTransportationGroup(t)),
          );
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  protected addDestination(): void {
    this.destinationsArray.push(this.createDestinationGroup());
  }

  protected removeDestination(index: number): void {
    this.destinationsArray.removeAt(index);
  }

  protected destinationActivities(index: number) {
    return this.destinationsArray.at(index).controls.activities;
  }

  protected addActivity(destinationIndex: number): void {
    this.destinationActivities(destinationIndex).push(this.createActivityGroup());
  }

  protected removeActivity(destinationIndex: number, activityIndex: number): void {
    this.destinationActivities(destinationIndex).removeAt(activityIndex);
  }

  protected toggleAccommodation(destinationIndex: number): void {
    const control = this.destinationsArray.at(destinationIndex).controls.accommodation;
    if (control.enabled) {
      control.disable();
    } else {
      control.enable();
    }
  }

  protected addTransportation(): void {
    this.transportationsArray.push(this.createTransportationGroup());
  }

  protected removeTransportation(index: number): void {
    this.transportationsArray.removeAt(index);
  }

  private buildDestinationPayload(index: number, group: ReturnType<typeof this.createDestinationGroup>): Destination {
    const raw = group.getRawValue();
    return {
      city: raw.city,
      country: raw.country,
      arrivalDate: raw.arrivalDate,
      departureDate: raw.departureDate,
      orderIndex: index,
      activities: raw.activities.map((a) => ({
        name: a.name,
        description: a.description || null,
        date: a.date,
        cost: a.cost,
      })),
      accommodation: group.controls.accommodation.enabled
        ? {
            name: raw.accommodation.name,
            type: raw.accommodation.type as Accommodation['type'],
            address: raw.accommodation.address,
            checkIn: raw.accommodation.checkIn,
            checkOut: raw.accommodation.checkOut,
          }
        : null,
    };
  }

  private buildTransportationPayload(
    group: ReturnType<typeof this.createTransportationGroup>,
  ): Transportation {
    const raw = group.getRawValue();
    return {
      type: raw.type as Transportation['type'],
      fromLocation: raw.fromLocation,
      toLocation: raw.toLocation,
      departureTime: toIsoInstant(raw.departureTime),
      arrivalTime: toIsoInstant(raw.arrivalTime),
      provider: raw.provider || null,
    };
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toastService.error('Certains champs sont manquants ou invalides (vérifiez les dates).');
      return;
    }

    if (this.destinationsArray.length === 0) {
      this.toastService.error('Ajoutez au moins une destination');
      return;
    }

    const raw = this.form.getRawValue();
    const request: TravelRequest = {
      title: raw.title,
      ownerId: raw.ownerId,
      startDate: raw.startDate,
      endDate: raw.endDate,
      status: raw.status as TravelRequest['status'],
      destinations: this.destinationsArray.controls.map((c, i) => this.buildDestinationPayload(i, c)),
      transportations: this.transportationsArray.controls.map((c) => this.buildTransportationPayload(c)),
    };

    this.saving.set(true);
    const id = this.travelId();
    const request$ = id ? this.travelsService.update(id, request) : this.travelsService.create(request);

    request$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.toastService.success(id ? 'Voyage mis à jour' : 'Voyage créé');
        this.router.navigate(['/travels']);
      },
      error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
    });
  }
}

import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { SlaClock, SlaPolicy, TicketPriority } from './models';

@Injectable({ providedIn: 'root' })
export class SlaApi {
  private readonly http = inject(HttpClient);

  private base(org: string): string {
    return `${environment.apiUrl}/organizations/${org}`;
  }

  ticket(org: string, ticketId: string): Promise<SlaClock[]> {
    return firstValueFrom(this.http.get<SlaClock[]>(`${this.base(org)}/tickets/${ticketId}/sla`));
  }

  open(org: string): Promise<SlaClock[]> {
    return firstValueFrom(this.http.get<SlaClock[]>(`${this.base(org)}/sla/open`));
  }

  policies(org: string): Promise<SlaPolicy[]> {
    return firstValueFrom(this.http.get<SlaPolicy[]>(`${this.base(org)}/sla/policies`));
  }

  updatePolicy(org: string, priority: TicketPriority, policy: Omit<SlaPolicy, 'priority'>): Promise<SlaPolicy> {
    return firstValueFrom(this.http.put<SlaPolicy>(`${this.base(org)}/sla/policies/${priority}`, policy));
  }
}

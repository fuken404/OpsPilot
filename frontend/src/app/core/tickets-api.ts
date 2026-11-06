import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuditEntry, TicketCategory, TicketDetail, TicketPriority, TicketStatus, TicketSummary } from './models';

export type TicketView = 'OPEN' | 'CLOSED' | 'ALL';
export type AssigneeFilter = 'ANY' | 'MINE' | 'UNASSIGNED';

export interface NewTicket {
  title: string;
  description: string;
  category: TicketCategory;
  priority: TicketPriority | null;
}

@Injectable({ providedIn: 'root' })
export class TicketsApi {
  private readonly http = inject(HttpClient);

  private base(org: string): string {
    return `${environment.apiUrl}/organizations/${org}/tickets`;
  }

  list(org: string, view: TicketView, assignee: AssigneeFilter, q: string): Promise<TicketSummary[]> {
    const params: Record<string, string> = { view, assignee };
    if (q.trim()) params['q'] = q.trim();
    return firstValueFrom(this.http.get<TicketSummary[]>(this.base(org), { params }));
  }

  create(org: string, ticket: NewTicket): Promise<TicketDetail> {
    return firstValueFrom(this.http.post<TicketDetail>(this.base(org), ticket));
  }

  get(org: string, id: string): Promise<TicketDetail> {
    return firstValueFrom(this.http.get<TicketDetail>(`${this.base(org)}/${id}`));
  }

  history(org: string, id: string): Promise<AuditEntry[]> {
    return firstValueFrom(this.http.get<AuditEntry[]>(`${this.base(org)}/${id}/history`));
  }

  changeStatus(org: string, ticket: TicketDetail, status: TicketStatus): Promise<TicketDetail> {
    return firstValueFrom(this.http.put<TicketDetail>(`${this.base(org)}/${ticket.id}/status`,
      { version: ticket.version, status }));
  }

  assign(org: string, ticket: TicketDetail, assigneeId: string | null): Promise<TicketDetail> {
    return firstValueFrom(this.http.put<TicketDetail>(`${this.base(org)}/${ticket.id}/assignee`,
      { version: ticket.version, assigneeId }));
  }

  triage(org: string, ticket: TicketDetail, priority: TicketPriority, category: TicketCategory): Promise<TicketDetail> {
    return firstValueFrom(this.http.patch<TicketDetail>(`${this.base(org)}/${ticket.id}`,
      { version: ticket.version, priority, category }));
  }

  attach(org: string, ticket: TicketDetail, file: File, internal: boolean): Promise<TicketDetail> {
    const form = new FormData();
    form.append('file', file);
    form.append('internal', String(internal));
    return firstValueFrom(this.http.post<TicketDetail>(`${this.base(org)}/${ticket.id}/attachments`, form));
  }

  /** Con el token de la sesión: un enlace directo no lo llevaría. */
  download(org: string, attachmentId: string): Promise<Blob> {
    return firstValueFrom(this.http.get(`${environment.apiUrl}/organizations/${org}/attachments/${attachmentId}`,
      { responseType: 'blob' }));
  }

  comment(org: string, ticket: TicketDetail, body: string, internal: boolean): Promise<TicketDetail> {
    return firstValueFrom(this.http.post<TicketDetail>(`${this.base(org)}/${ticket.id}/comments`, { body, internal }));
  }
}

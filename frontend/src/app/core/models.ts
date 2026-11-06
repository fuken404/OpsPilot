export type Role = 'ADMIN' | 'TECHNICIAN' | 'CUSTOMER';

export interface MyMembership {
  organizationId: string;
  name: string;
  slug: string;
  role: Role;
}

export interface Me {
  id: string;
  email: string;
  displayName: string;
  memberships: MyMembership[];
}

export interface Member {
  membershipId: string | null;
  userId: string | null;
  email: string;
  displayName: string;
  role: Role;
  pending: boolean;
}

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrador',
  TECHNICIAN: 'Técnico',
  CUSTOMER: 'Cliente',
};

export type TicketStatus = 'NEW' | 'IN_PROGRESS' | 'WAITING_CUSTOMER' | 'RESOLVED' | 'CLOSED' | 'CANCELLED';
export type TicketPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type TicketCategory = 'HARDWARE' | 'SOFTWARE' | 'NETWORK' | 'ACCESS' | 'OTHER';

export interface Person {
  id: string;
  displayName: string;
  email: string;
}

export interface TicketSummary {
  id: string;
  number: number;
  title: string;
  category: TicketCategory;
  priority: TicketPriority;
  status: TicketStatus;
  requester: Person | null;
  assignee: Person | null;
  createdAt: string;
  updatedAt: string;
}

export interface TicketComment {
  id: string;
  author: Person | null;
  body: string;
  internal: boolean;
  createdAt: string;
}

export interface TicketAttachment {
  id: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploader: Person | null;
  internal: boolean;
  createdAt: string;
}

export interface TicketDetail extends TicketSummary {
  description: string;
  resolvedAt: string | null;
  closedAt: string | null;
  version: number;
  nextStatuses: TicketStatus[];
  canTriage: boolean;
  canAssign: boolean;
  canComment: boolean;
  canCommentInternally: boolean;
  comments: TicketComment[];
  attachments: TicketAttachment[];
}

export interface AuditEntry {
  id: string;
  action: 'CREATED' | 'STATUS_CHANGED' | 'ASSIGNED' | 'TRIAGED' | 'COMMENTED' | 'ATTACHED' | 'SLA_WARNING' | 'SLA_BREACHED';
  actor: { id: string; displayName: string } | null;
  details: Record<string, unknown>;
  occurredAt: string;
}

export const STATUS_LABELS: Record<TicketStatus, string> = {
  NEW: 'Nuevo',
  IN_PROGRESS: 'En curso',
  WAITING_CUSTOMER: 'Esperando al cliente',
  RESOLVED: 'Resuelto',
  CLOSED: 'Cerrado',
  CANCELLED: 'Cancelado',
};

/** Cómo se llama el botón que lleva a cada estado. */
export const STATUS_ACTIONS: Record<TicketStatus, string> = {
  NEW: 'Nuevo',
  IN_PROGRESS: 'Poner en curso',
  WAITING_CUSTOMER: 'Esperar al cliente',
  RESOLVED: 'Marcar resuelto',
  CLOSED: 'Cerrar',
  CANCELLED: 'Cancelar ticket',
};

export const PRIORITY_LABELS: Record<TicketPriority, string> = {
  CRITICAL: 'Crítica',
  HIGH: 'Alta',
  MEDIUM: 'Media',
  LOW: 'Baja',
};

export const CATEGORY_LABELS: Record<TicketCategory, string> = {
  HARDWARE: 'Hardware',
  SOFTWARE: 'Software',
  NETWORK: 'Red',
  ACCESS: 'Accesos',
  OTHER: 'Otro',
};

export const PRIORITIES = Object.keys(PRIORITY_LABELS) as TicketPriority[];
export const CATEGORIES = Object.keys(CATEGORY_LABELS) as TicketCategory[];

export type SlaKind = 'RESPONSE' | 'RESOLUTION';
export type SlaState = 'RUNNING' | 'AT_RISK' | 'BREACHED' | 'PAUSED' | 'MET' | 'STOPPED';

export interface SlaClock {
  ticketId: string;
  kind: SlaKind;
  state: SlaState;
  targetMinutes: number;
  businessHours: boolean;
  startedAt: string;
  dueAt: string | null;
  metAt: string | null;
  pausedSince: string | null;
  breachedAt: string | null;
  remainingMinutes: number | null;
  late: boolean;
}

export interface SlaPolicy {
  priority: TicketPriority;
  responseMinutes: number;
  resolutionMinutes: number;
  businessHours: boolean;
}

export const SLA_KIND_LABELS: Record<SlaKind, string> = {
  RESPONSE: 'Primera respuesta',
  RESOLUTION: 'Resolución',
};

/**
 * "2 h 15 min", "45 min", "3 d 2 h". Sin días para tiempos laborales: 24 h hábiles no son un día,
 * son casi tres jornadas.
 */
export function formatMinutes(total: number, withDays = true): string {
  const minutes = Math.abs(Math.round(total));
  const days = withDays ? Math.floor(minutes / 1440) : 0;
  const hours = Math.floor((minutes - days * 1440) / 60);
  const rest = minutes % 60;
  const parts: string[] = [];
  if (days) parts.push(`${days} d`);
  if (hours) parts.push(`${hours} h`);
  if (rest && !days) parts.push(`${rest} min`);
  return parts.length ? parts.join(' ') : '0 min';
}

export interface Dashboard {
  generatedAt: string;
  open: number;
  unassigned: number;
  byStatus: Record<TicketStatus, number>;
  byPriority: Record<TicketPriority, number>;
  sla: { running: number; atRisk: number; breached: number; paused: number };
  compliance: { response: number | null; responseTotal: number; resolution: number | null; resolutionTotal: number };
  workload: { userId: string; displayName: string; role: Role; open: number; breached: number }[];
  trend: { date: string; created: number; resolved: number }[];
  averageResolutionHours: number | null;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

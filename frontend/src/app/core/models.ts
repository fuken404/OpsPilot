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
}

export interface AuditEntry {
  id: string;
  action: 'CREATED' | 'STATUS_CHANGED' | 'ASSIGNED' | 'TRIAGED' | 'COMMENTED';
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

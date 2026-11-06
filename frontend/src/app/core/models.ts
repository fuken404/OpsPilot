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

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { Me } from './models';
import { Session } from './session';

const ME: Me = {
  id: 'u1',
  email: 'ana@demo',
  displayName: 'Ana',
  memberships: [
    { organizationId: 'o1', name: 'Acme', slug: 'acme', role: 'ADMIN' },
    { organizationId: 'o2', name: 'Nimbus', slug: 'nimbus', role: 'CUSTOMER' },
  ],
};

describe('Session', () => {
  let session: Session;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: OidcSecurityService, useValue: {} }],
    });
    session = TestBed.inject(Session);
    http = TestBed.inject(HttpTestingController);
  });

  async function loadMe(): Promise<void> {
    const loading = session.load();
    http.expectOne((req) => req.url.endsWith('/me')).flush(ME);
    await loading;
  }

  it('elige la primera organización y su rol', async () => {
    await loadMe();
    expect(session.organization()?.slug).toBe('acme');
    expect(session.can('ADMIN')).toBe(true);
  });

  it('cambia de organización y recalcula los permisos', async () => {
    await loadMe();
    session.select('o2');
    expect(session.organization()?.slug).toBe('nimbus');
    expect(session.can('ADMIN', 'TECHNICIAN')).toBe(false);
    expect(localStorage.getItem('opspilot.organization')).toBe('o2');
  });

  it('avisa si la API no responde', async () => {
    const loading = session.load();
    http.expectOne((req) => req.url.endsWith('/me')).error(new ProgressEvent('error'));
    await loading;
    expect(session.error()).toContain('API');
  });
});

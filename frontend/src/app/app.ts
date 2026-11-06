import { Component, inject, OnInit } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App implements OnInit {
  private readonly oidc = inject(OidcSecurityService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    // Procesa la vuelta desde Keycloak y lleva a la consola si hay sesión.
    this.oidc.checkAuth().subscribe(({ isAuthenticated }) => {
      // Al volver de Keycloak la URL aún trae ?code=...: se compara solo la ruta.
      if (isAuthenticated && location.pathname === '/') {
        void this.router.navigateByUrl('/app');
      }
    });
  }
}

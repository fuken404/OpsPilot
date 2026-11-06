import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { authInterceptor, LogLevel, provideAuth } from 'angular-auth-oidc-client';
import { environment } from '../environments/environment';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor()])),
    provideAuth({
      config: {
        authority: environment.oidc.authority,
        clientId: environment.oidc.clientId,
        redirectUrl: window.location.origin,
        postLogoutRedirectUri: window.location.origin,
        scope: 'openid profile email',
        responseType: 'code', // Authorization Code + PKCE: el navegador nunca ve un secreto
        silentRenew: true,
        useRefreshToken: true,
        secureRoutes: [environment.apiUrl], // el token solo viaja a nuestra API
        logLevel: LogLevel.Warn,
      },
    }),
  ],
};

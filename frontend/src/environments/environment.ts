// Producción: se completa al desplegar.
export const environment = {
  apiUrl: '/api',
  // Mismo dominio que la página: wss://<host>/ws
  wsUrl: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
  oidc: {
    authority: 'https://auth.opspilot.fuken404.dev/realms/opspilot',
    clientId: 'opspilot-web',
  },
};

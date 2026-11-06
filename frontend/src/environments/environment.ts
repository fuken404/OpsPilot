// Producción: el frontend vive en Vercel; la API y Keycloak, en el servidor de Oracle.
export const environment = {
  apiUrl: 'https://api.opspilot.fuken404.dev/api',
  wsUrl: 'wss://api.opspilot.fuken404.dev/ws',
  oidc: {
    authority: 'https://auth.opspilot.fuken404.dev/realms/opspilot',
    clientId: 'opspilot-web',
  },
};

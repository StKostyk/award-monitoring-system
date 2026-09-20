import { AuthConfig } from 'angular-oauth2-oidc';

import { environment } from '../../../environments/environment';

export const authConfig: AuthConfig = {
  issuer: environment.issuer,
  clientId: 'award-web',
  redirectUri: `${window.location.origin}/callback`,
  postLogoutRedirectUri: window.location.origin,
  responseType: 'code',
  scope: 'openid profile',
  showDebugInformation: !environment.production,
  strictDiscoveryDocumentValidation: true,
  clearHashAfterLogin: true,
};

# ADR-013: Frontend Framework Selection

**Status**: Accepted  
**Date**: 2025-08-21  
**Author**: Stefan Kostyk  
**Stakeholders**: Project Architect, Frontend Team, UX Team

---

## Context

The Award Monitoring & Tracking System requires a modern, enterprise-grade frontend framework capable of handling complex user interfaces, internationalization, and integration with backend APIs. The framework must support TypeScript-first development and provide comprehensive tooling for a solo developer environment.

### Background
- Need for a comprehensive frontend framework with enterprise features
- Requirement for TypeScript-first development approach
- Complex UI requirements including forms, data tables, and dashboard views
- Internationalization support for English and Ukrainian languages
- Integration with Spring Boot backend APIs

### Assumptions
- Solo developer context requiring excellent tooling and documentation
- Enterprise adoption and community support are critical for long-term viability
- TypeScript expertise is valuable for portfolio demonstration
- Framework should support both traditional and progressive web app approaches

---

## Decision

**Angular 20+** has been selected as the frontend framework for the Award Monitoring & Tracking System.

### Chosen Approach
- **Framework**: Angular 20+ (latest stable version)
- **Language**: TypeScript as primary development language
- **Architecture**: Component-based architecture with services and modules
- **Build Tool**: Angular CLI for project scaffolding and build management

### Rationale
- **Enterprise Adoption**: Widely adopted in enterprise environments with strong Google backing
- **TypeScript-First**: Native TypeScript support with excellent tooling and type safety
- **Comprehensive Framework**: Batteries-included approach with routing, HTTP client, forms, and testing
- **Community Support**: Large community, extensive documentation, and third-party libraries
- **Long-term Support**: LTS versions provide stability for enterprise applications

---

## Consequences

### Positive Consequences
- **Developer Productivity**: Comprehensive CLI tooling and IDE support
- **Type Safety**: TypeScript provides compile-time error checking and better refactoring
- **Enterprise Features**: Built-in dependency injection, routing, and HTTP interceptors
- **Testing Support**: Comprehensive testing framework with Karma and Jasmine
- **Documentation**: Excellent official documentation and learning resources

### Negative Consequences
- **Learning Curve**: Steeper learning curve compared to React for developers new to Angular
- **Bundle Size**: Larger initial bundle size compared to lightweight alternatives
- **Framework Lock-in**: Strong opinions and conventions may limit flexibility

### Neutral Consequences
- **Release Cycle**: Regular major version releases require periodic updates
- **Ecosystem**: Large ecosystem with both benefits and complexity

---

## Alternatives Considered

### Alternative 1: React
- **Pros**: Large ecosystem, flexible, lightweight, strong job market
- **Cons**: Requires additional libraries for complete solution, less opinionated
- **Reason for Rejection**: Angular's comprehensive approach better suits enterprise requirements

### Alternative 2: Vue.js
- **Pros**: Gentle learning curve, good performance, flexible architecture
- **Cons**: Smaller enterprise adoption, less comprehensive tooling
- **Reason for Rejection**: Lower enterprise adoption and TypeScript integration

---

## Implementation Notes

### Technical Requirements
- **Angular Version**: 20+ for latest features and performance improvements
- **Node.js**: 18+ for Angular CLI and build tools
- **TypeScript**: Latest version supported by Angular
- **Package Manager**: npm or yarn for dependency management

### Project Structure
```
src/
├── app/
│   ├── core/              # Singleton services, guards
│   ├── shared/            # Shared components, pipes, directives
│   ├── features/          # Feature modules
│   │   ├── awards/
│   │   ├── users/
│   │   └── reports/
│   ├── layout/            # Layout components
│   └── assets/            # Static assets
├── environments/          # Environment configurations
└── styles/               # Global styles
```

### Development Setup
```bash
npm install -g @angular/cli@latest
ng new award-monitoring-system --routing --style=scss
ng add @angular/material
ng add @ngrx/store
```

---

## Compliance & Quality

### Security Implications
- **CSRF Protection**: Built-in CSRF protection with HTTP interceptors
- **XSS Prevention**: Angular's sanitization and template security
- **Authentication Integration**: Seamless integration with JWT tokens

### Performance Impact
- **Bundle Optimization**: Tree shaking and lazy loading for optimal bundle size
- **Change Detection**: OnPush strategy for performance optimization
- **Service Workers**: PWA capabilities for offline functionality

### Maintainability
- **Code Organization**: Modular architecture with clear separation of concerns
- **Testing**: Unit testing with Jasmine/Karma, E2E testing with Cypress
- **Linting**: Angular ESLint rules for code quality

---

## Success Metrics

### Key Performance Indicators
- **Bundle Size**: < 500KB gzipped for initial load
- **Time to Interactive**: < 3 seconds on 3G connection
- **Lighthouse Score**: > 90 for performance and accessibility

### Monitoring & Alerting
- **Core Web Vitals**: Monitor LCP, FID, and CLS metrics
- **Error Tracking**: Integration with error monitoring service
- **Performance Budget**: Automated checks for bundle size increases

---

## Related Documents

- **Technology Stack**: [Technology Stack Selection](../TECH_STACK.md)
- **Other ADRs**: [ADR-014 UI Component Library](./ADR-014-UI-Component-Library.md)
- **External Resources**: [Angular Documentation](https://angular.io/docs)

---

## Addendum 2026-09: Rendering Mode

The application is rendered client-side only. Server-side rendering (SSR) was part of the initial scaffold but removed before feature development:

- Every screen sits behind authentication, so SSR brings no SEO or first-paint benefit for anonymous visitors.
- SSR requires a Node runtime in the container and hydration-safe code around browser-only APIs (token storage, PKCE), which adds complexity without user value.
- The static bundle is served by nginx (see `frontend/Dockerfile`), which matches ADR-017.

Localisation is provided at runtime with Transloco (`@jsverse/transloco`): one build serves both languages, Ukrainian is the default, English is switched from the toolbar and the choice is remembered in the browser. The compile-time `$localize` approach was considered and rejected because it produces one bundle per language, doubles the routes and redirect URIs the authorization server must know, and makes the language switch a full reload.

Authentication in the browser uses `angular-oauth2-oidc` as a public OpenID Connect client of the application's own authorization server (authorization code flow with PKCE); tokens are kept in session storage and refreshed automatically.

---

## Addendum 2026-10: Service worker and offline policy

The production build is an installable web app (Feature 8.1, US-013). Offline support is a fallback for the award form, not an offline-first client:

- **`@angular/service-worker` over Workbox.** It ships with Angular, writes a hashed manifest (`ngsw.json`) per build and offers the `SwUpdate` API; no extra build step. It is registered in the production build only (`registerWhenStable:30000`); `npm start` and the e2e dev build run without it.
- **Cache policy** (`frontend/ngsw-config.json`). Asset groups hold the shell only: `index.html`, the bundles, `favicon.ico`, the translations and the fonts. The single data group `brand` (`/brand/**`, freshness, 3 s timeout, 7 days) keeps the brand file, manifest and icons, which a deployment replaces without a rebuild and which hold no personal data. No API response is cached.
- **Backend navigations bypass the worker.** `navigationUrls` exclude `/api/`, `/oauth2/`, `/connect/`, `/.well-known/`, `/login`, `/logout`, `/error`, `/login-assets/`, `/swagger-ui/`, `/v3/api-docs`, `/privacy/<version>/` and `/health`, so the sign-in page and the authorization flow never receive the cached shell. A unit spec reads the configuration and fails on a cached backend path or a missing exclusion.
- **Connectivity.** `ConnectivityService` combines the browser's `online`/`offline` events with the outcome of backend requests (status 0 marks the server unreachable, a successful answer reachable again); components read its `online` signal. Without a connection, start-up keeps the stored session (discovery document and profile are loaded once the server answers, a failed token renewal is retried once), a visitor without a session gets the offline page instead of the sign-in redirect, and sign-out drops the tokens on the device only.
- **Updates.** `UpdatePromptService` checks for a new version at start, when the tab becomes visible and every 6 hours, and offers it in a snackbar; the user decides when to reload. An unrecoverable worker state reloads the page.
- **Worker files** (`ngsw-worker.js`, `ngsw.json`, `safety-worker.js`) are served with `Cache-Control: no-cache`, the manifest as `application/manifest+json`; the content security policy is unchanged (`script-src 'self'` covers the same-origin worker).
- **Kill switch.** Serving `safety-worker.js` as `ngsw-worker.js`, or removing `ngsw.json`, makes the worker unregister itself on its next check; the application keeps working without it.

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2025-08-21 | Stefan Kostyk | Initial version | Document creation |
| 2026-09-19 | Stefan Kostyk | Addendum: client-side rendering only, i18n locales | Scaffold cleanup before Sprint 2 |
| 2026-09-21 | Stefan Kostyk | Runtime localisation with Transloco; OpenID Connect client library | Feature 1.1, story 1.1.1 |
| 2026-10-26 | Stefan Kostyk | Addendum: service worker and offline policy | Feature 8.1, story 8.1.1 |

---

**Document Status**: Approved  
**Next Review Date**: 2026-02-21  
**ADR Category**: Technology 
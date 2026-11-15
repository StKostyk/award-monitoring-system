# ADR-014: UI Component Library Selection

**Status**: Accepted  
**Date**: 2025-08-21  
**Author**: Stefan Kostyk  
**Stakeholders**: Project Architect, Frontend Team, UX Team

---

## Context

The Award Monitoring & Tracking System requires a comprehensive UI component library that provides consistent, accessible, and professional-looking components. The library must integrate seamlessly with Angular and support theming, internationalization, and accessibility requirements.

### Background
- Need for consistent design system across the application
- Accessibility requirements (WCAG AA compliance)
- Professional appearance suitable for enterprise environments
- Support for theming and customization
- Integration with Angular ecosystem

### Assumptions
- Google-backed libraries provide better long-term support and integration
- Material Design principles align with modern enterprise UI expectations
- Comprehensive component library reduces development time
- Built-in accessibility features are essential for compliance

---

## Decision

**Angular Material** has been selected as the UI component library for the Award Monitoring & Tracking System.

### Chosen Approach
- **Component Library**: Angular Material (latest stable version)
- **Design System**: Google Material Design 3
- **Theming**: Custom theme based on Material Design tokens
- **Icons**: Material Icons with custom icon additions as needed

### Rationale
- **Google-Backed**: Official Google support ensures long-term viability and integration
- **Accessibility Features**: Built-in WCAG AA compliance and accessibility features
- **Consistent Design**: Material Design provides cohesive and professional appearance
- **Angular Integration**: Purpose-built for Angular with excellent TypeScript support
- **Comprehensive Components**: Wide range of components covering most enterprise use cases

---

## Consequences

### Positive Consequences
- **Development Speed**: Pre-built components accelerate development
- **Accessibility**: Built-in accessibility features reduce compliance effort
- **Consistency**: Unified design language across the application
- **Maintenance**: Google backing ensures regular updates and bug fixes
- **Documentation**: Excellent documentation and examples

### Negative Consequences
- **Design Constraints**: Material Design may limit unique design expressions
- **Bundle Size**: Adds to application bundle size
- **Learning Curve**: Developers need to learn Material Design principles

### Neutral Consequences
- **Theming Complexity**: Advanced theming requires understanding of Material Design tokens
- **Customization**: Some components may require additional customization for specific needs

---

## Alternatives Considered

### Alternative 1: PrimeNG
- **Pros**: Rich component set, extensive features, good documentation
- **Cons**: Less Angular-native feel, additional licensing for advanced features
- **Reason for Rejection**: Angular Material provides better ecosystem integration

### Alternative 2: Nebular
- **Pros**: Eva Design System, good theming capabilities, Angular-focused
- **Cons**: Smaller community, less enterprise adoption
- **Reason for Rejection**: Lower adoption and smaller ecosystem

### Alternative 3: Custom Component Library
- **Pros**: Complete control over design and functionality
- **Cons**: Significant development time, maintenance overhead, no accessibility built-in
- **Reason for Rejection**: Not practical for solo developer timeframe

---

## Implementation Notes

### Technical Requirements
- **Angular Material Version**: Latest compatible with Angular 20+
- **CDK**: Angular Component Development Kit for advanced component behaviors
- **Animations**: Angular Animations for Material components
- **Icons**: Material Icons and custom SVG icons

### Installation and Setup
```bash
ng add @angular/material
npm install @angular/cdk
npm install @angular/animations
```

### Theme Configuration
```scss
@use '@angular/material' as mat;

$primary-palette: mat.define-palette(mat.$blue-palette);
$accent-palette: mat.define-palette(mat.$amber-palette);
$warn-palette: mat.define-palette(mat.$red-palette);

$theme: mat.define-light-theme((
  color: (
    primary: $primary-palette,
    accent: $accent-palette,
    warn: $warn-palette,
  )
));

@include mat.all-component-themes($theme);
```

### Core Components Usage
- **Navigation**: Toolbar, Sidenav, Menu
- **Layout**: Card, Expansion Panel, Tabs, Stepper
- **Forms**: Input, Select, Checkbox, Radio, Datepicker
- **Data Display**: Table, List, Paginator, Sort
- **Feedback**: Dialog, Snackbar, Progress Bar, Spinner

---

## Compliance & Quality

### Accessibility Implications
- **WCAG AA Compliance**: Built-in accessibility features in all components
- **Screen Reader Support**: Proper ARIA labels and live regions
- **Keyboard Navigation**: Full keyboard navigation support
- **High Contrast**: Support for high contrast themes

### Performance Impact
- **Tree Shaking**: Import only needed components to minimize bundle size
- **OnPush Strategy**: Compatible with Angular OnPush change detection
- **Virtual Scrolling**: Available for large datasets in tables and lists

### Maintainability
- **Consistent API**: Standardized component API across all Material components
- **TypeScript Support**: Full TypeScript definitions and IntelliSense
- **Testing**: Angular Testing Utilities for Material components

---

## Success Metrics

### Key Performance Indicators
- **Accessibility Score**: 100% axe-core compliance
- **Development Speed**: 50% reduction in UI development time
- **Bundle Size Impact**: < 200KB additional bundle size

### Monitoring & Alerting
- **Accessibility Testing**: Automated accessibility testing in CI/CD
- **Bundle Size Monitoring**: Track Material components impact on bundle size
- **User Experience**: Monitor user interaction patterns with Material components

---

## Addendum 2026-10: Chart library

Angular Material has no charts. The personal dashboard (Feature 5.1) uses **ngx-echarts 21 on ECharts 6**:

- **SVG renderer**: crisp in print and at any zoom, and every mark is a DOM node that tests and audits can inspect.
  ng2-charts draws on canvas, which gives screen readers nothing and prints blurred, and its peer range already
  requires Angular 22.
- **Bundle**: tree-shaken imports from `echarts/core` (bar and scatter charts; grid, tooltip, legend and aria
  components; the SVG renderer) loaded with the lazy `dashboard` route (`provideEchartsCore` with a dynamic
  import), so the initial bundle does not grow.
- **Wrapper**: `shared/charts/ChartComponent` adds the ARIA description, theme text colours, no animation under
  `prefers-reduced-motion`, and a "Show as table" switch that replaces the chart with a table of the same figures.
- **Colours**: theme tokens `--app-chart-bar` and `--app-chart-band-1..5` (one blue hue, five steps per scheme,
  checked for monotone lightness, visible step gaps and at least 2:1 against the surface in light and dark), read
  by `ChartThemeService` and re-read when the colour scheme changes. Nine recognition levels cannot be told apart in
  one hue, so stacked charts fold the five levels below the university into one band; tables and the level chart
  keep all nine.

## Addendum 2026-11-14: Lucide icons (Feature 9.3)

Material Icons (a ligature font of about 2 000 glyphs) give way to **Lucide** line icons (`lucide-angular`), which
match the thin strokes of the new look and reach the bundle one by one:

- `shared/icons/icons.ts` lists every icon the app uses under a short name; `<app-icon name="…">` draws it as inline
  SVG in the current text colour, sized by `--app-icon-size`. Icons not listed there stay out of the bundle.
- Icons are decorative (`aria-hidden`); a control that shows only an icon carries an `aria-label`.
- The lint rule `no-restricted-imports` blocks `@angular/material/icon`, so `<mat-icon>` does not come back.
  Material components stay as they are; only their icon content changes.

| Option | Decision |
|--------|----------|
| Keep Material Icons (font) | Rejected: one font file for a handful of glyphs, filled style that clashes with the new look |
| Material Symbols (variable font) | Rejected: larger font, same ligature approach |
| Lucide SVGs registered per use | **Chosen**: tree-shaken, stroke style, no font request |

---

## Addendum 2026-11-15: Motion, effect components and chart colours (Feature 9.3)

**Motion.** CSS and Angular `animate.enter`/`animate.leave` stay the default. The `motion` package (its `animate`
function only) covers what CSS does poorly: counting a number up, staggering list entries and springs. It is wrapped
by `MotionHelper` in `shared/effects`, which sets the end state at once under `prefers-reduced-motion: reduce` and
stops every animation through the owning component's `DestroyRef`. Only lazy routes import it, so the initial bundle
does not grow.

**Effect components.** Six home-made standalone components replace a third-party effects kit: beam, border beam,
spotlight (decorative, `aria-hidden`), ticker, marquee and typed text (their text stays readable by screen readers in
a static copy). They move only `transform` and `opacity`, take colours from tokens and stand still under reduced
motion.

**Chart colours.** The single blue hue of the 2026-10 addendum gives way to the theme: `--app-chart-1..8` are
categorical series colours (ECharts `color`, used for lines per unit), `--app-chart-bar` is `--app-chart-1`, and
`--app-chart-band-1..5` stay one hue per scheme (burgundy in light, cyan in dark) with the same checks: monotone
lightness and at least 2:1 against the surfaces (series colours at least 3:1). `ChartThemeService` resolves all of
them again when the scheme changes.

## Related Documents

- **Technology Stack**: [Technology Stack Selection](../TECH_STACK.md)
- **Other ADRs**: [ADR-013 Frontend Framework](./ADR-013-Frontend-Framework.md), [ADR-016 Styling](./ADR-016-Styling.md)
- **External Resources**: [Angular Material Documentation](https://material.angular.io/)

---

## Revision History

| **Date** | **Author** | **Changes** | **Reason** |
|----------|------------|-------------|------------|
| 2025-08-21 | Stefan Kostyk | Initial version | Document creation |
| 2026-10-19 | Stefan Kostyk | Addendum: chart library | Personal dashboard (Feature 5.1) |
| 2026-11-14 | Stefan Kostyk | Addendum: Lucide icons | New look (Feature 9.3) |
| 2026-11-15 | Stefan Kostyk | Addendum: motion, effect components, chart colours | New look (Feature 9.3) |

---

**Document Status**: Approved  
**Next Review Date**: 2026-02-21  
**ADR Category**: Technology 
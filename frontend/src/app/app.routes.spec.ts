import { routes } from './app.routes';
import { ShellComponent } from './core/layout/shell.component';
import { NotFoundComponent } from './features/not-found/not-found.component';

describe('app routes', () => {
  const shell = routes.find((route) => route.component === ShellComponent);

  it('ac1_2 shows the not-found page inside the shell for an unknown path', () => {
    const fallback = shell?.children?.at(-1);

    expect(fallback?.path).toBe('**');
    expect(fallback?.component).toBe(NotFoundComponent);
    expect(fallback?.title).toBe('titles.notFound');
  });

  it('ac1_2 keeps the typed address instead of redirecting home', () => {
    expect(routes.some((route) => route.path === '**' && route.redirectTo !== undefined)).toBe(
      false,
    );
    expect(shell?.children?.some((route) => route.path === '**' && route.redirectTo)).toBe(false);
  });
});

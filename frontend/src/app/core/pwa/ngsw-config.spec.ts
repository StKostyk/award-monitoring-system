import config from '../../../../ngsw-config.json';

/** Sample requests of every path the backend or nginx answers (PRD 8.1 §4 definitions). */
const BACKEND_URLS = [
  '/api/v1/awards',
  '/api/v1/users/me',
  '/oauth2/authorize?response_type=code&client_id=awards-spa',
  '/oauth2/token',
  '/connect/logout',
  '/.well-known/openid-configuration',
  '/login',
  '/login?error',
  '/logout',
  '/error',
  '/login-assets/login.css',
  '/swagger-ui/index.html',
  '/v3/api-docs',
  '/v3/api-docs/swagger-config',
  '/privacy/1.0/uk.html',
  '/health',
];

const APP_ROUTES = [
  '/',
  '/dashboard',
  '/awards/new',
  '/awards/0b6f/edit',
  '/callback?code=abc&state=x',
  '/privacy',
  '/privacy-notice',
  '/public/achievements',
];

/** The glob syntax of the service worker configuration: `**` spans segments, `*` stays in one. */
function globToRegex(glob: string): RegExp {
  const pattern = glob
    .split('**')
    .map((part) =>
      part
        .split('*')
        .map((text) => text.replace(/[.+?^${}()|[\]\\]/g, '\\$&'))
        .join('[^/]*'),
    )
    .join('.*');
  return new RegExp(`^${pattern}$`);
}

function matches(url: string, globs: string[]): boolean {
  const path = url.split('?')[0];
  return globs.some((glob) => globToRegex(glob).test(path));
}

function isNavigation(url: string): boolean {
  const includes = config.navigationUrls.filter((glob) => !glob.startsWith('!'));
  const excludes = config.navigationUrls
    .filter((glob) => glob.startsWith('!'))
    .map((glob) => glob.slice(1));
  return matches(url, includes) && !matches(url, excludes);
}

describe('ngsw-config.json', () => {
  const assetGlobs = config.assetGroups.flatMap((group) => group.resources.files);

  it('ac1_5_has_brand_as_its_only_data_group_with_the_freshness_strategy', () => {
    expect(config.dataGroups.map((group) => group.name)).toEqual(['brand']);
    const brand = config.dataGroups[0];
    expect(brand.urls).toEqual(['/brand/**']);
    expect(brand.cacheConfig).toEqual(
      expect.objectContaining({ strategy: 'freshness', timeout: '3s', maxAge: '7d' }),
    );
  });

  it('ac1_5_caches_no_backend_path', () => {
    const cached = [...assetGlobs, ...config.dataGroups.flatMap((group) => group.urls)];

    expect(cached.filter((glob) => glob.includes('/api'))).toEqual([]);
    for (const url of BACKEND_URLS) {
      expect(matches(url, cached), url).toBe(false);
    }
  });

  it('ac1_5_keeps_the_brand_files_out_of_the_hashed_shell', () => {
    for (const url of [
      '/brand/brand.json',
      '/brand/manifest.webmanifest',
      '/brand/icons/icon-192.png',
    ]) {
      expect(matches(url, assetGlobs), url).toBe(false);
    }
  });

  it('ac1_6_sends_navigations_to_backend_paths_to_the_network', () => {
    for (const url of BACKEND_URLS) {
      expect(isNavigation(url), url).toBe(false);
    }
  });

  it('ac1_7_serves_the_shell_for_application_routes', () => {
    for (const url of APP_ROUTES) {
      expect(isNavigation(url), url).toBe(true);
    }
  });
});

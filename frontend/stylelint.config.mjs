/** Colours come from the theme tokens only: no hex, rgb, hsl or named colour in component styles. */
export default {
  customSyntax: 'postcss-scss',
  rules: {
    'color-no-hex': true,
    'color-named': 'never',
    'function-disallowed-list': ['rgb', 'rgba', 'hsl', 'hsla'],
  },
};

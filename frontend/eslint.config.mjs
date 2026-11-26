// ESLint flat config for Angular + NgRx project
import eslint from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    ignores: ['dist/**', 'node_modules/**', '.angular/**'],
  },
  eslint.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.ts'],
    rules: {
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-empty-function': 'off',
      '@typescript-eslint/no-inferrable-types': 'off',
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: '@angular/material/icon',
              message: 'Icons are Lucide SVGs: use app-icon from shared/icons.',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['src/app/**/*.component.ts'],
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector:
            "Property[key.name='styles'] TemplateElement[value.raw=/#[0-9a-fA-F]{3,8}\\b|\\b(rgba?|hsla?)\\(/]",
          message: 'Colours come from the theme tokens: no hex, rgb or hsl in component styles.',
        },
        {
          selector:
            "Property[key.name='styles'] Literal[value=/#[0-9a-fA-F]{3,8}\\b|\\b(rgba?|hsla?)\\(/]",
          message: 'Colours come from the theme tokens: no hex, rgb or hsl in component styles.',
        },
      ],
    },
  }
);

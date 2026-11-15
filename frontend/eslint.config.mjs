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
  }
);

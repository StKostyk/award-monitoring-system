import { HttpErrorResponse } from '@angular/common/http';

import { fieldProblems, readProblem } from './problem';

describe('problem', () => {
  it('maps_a_failed_read_to_denied_gone_or_failed', () => {
    expect(readProblem(new HttpErrorResponse({ status: 403 }))).toBe('denied');
    expect(readProblem(new HttpErrorResponse({ status: 404 }))).toBe('gone');
    expect(readProblem(new HttpErrorResponse({ status: 503 }))).toBe('failed');
    expect(readProblem(new Error('offline'))).toBe('failed');
  });

  it('ac1_1_reads_the_field_errors_of_a_problem', () => {
    const problem = new HttpErrorResponse({
      status: 422,
      error: { errors: [{ field: 'title', code: 'required', message: 'x' }] },
    });

    expect(fieldProblems(problem).map((entry) => entry.field)).toEqual(['title']);
    expect(fieldProblems(new HttpErrorResponse({ status: 500 }))).toEqual([]);
  });
});

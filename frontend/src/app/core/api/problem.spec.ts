import { HttpErrorResponse } from '@angular/common/http';

import { fieldProblems } from './problem';

describe('problem', () => {
  it('ac1_1_reads_the_field_errors_of_a_problem', () => {
    const problem = new HttpErrorResponse({
      status: 422,
      error: { errors: [{ field: 'title', code: 'required', message: 'x' }] },
    });

    expect(fieldProblems(problem).map((entry) => entry.field)).toEqual(['title']);
    expect(fieldProblems(new HttpErrorResponse({ status: 500 }))).toEqual([]);
  });
});

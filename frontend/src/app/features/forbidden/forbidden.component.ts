import { Component } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-forbidden',
  imports: [MatCard, MatCardHeader, MatCardTitle, MatCardContent, MatButton, RouterLink, TranslocoPipe],
  templateUrl: './forbidden.component.html',
  styles: '.forbidden__card { max-width: 560px; }',
})
export class ForbiddenComponent {}

import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-legal-layout',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './legal-layout.component.html',
  styleUrl: './legal-layout.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LegalLayoutComponent {
  readonly eyebrow = input.required<string>();
  readonly title = input.required<string>();
}

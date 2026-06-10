import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import {
  LucideArrowRightLeft,
  LucideBook,
  LucideBriefcase,
  LucideCar,
  LucideCircle,
  LucideDumbbell,
  LucideHeart,
  LucideHome,
  LucideMonitor,
  LucideMusic,
  LucideRepeat,
  LucideShirt,
  LucideTrendingUp,
  LucideUtensils,
  LucideZap,
} from '@lucide/angular';
import { CategoryResponse } from '../../../core/models/category.models';

@Component({
  selector: 'app-category-badge',
  standalone: true,
  imports: [
    LucideArrowRightLeft,
    LucideBook,
    LucideBriefcase,
    LucideCar,
    LucideCircle,
    LucideDumbbell,
    LucideHeart,
    LucideHome,
    LucideMonitor,
    LucideMusic,
    LucideRepeat,
    LucideShirt,
    LucideTrendingUp,
    LucideUtensils,
    LucideZap,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="badge"
      [style.background-color]="bgColor()"
      [style.border-color]="borderColor()"
      [style.color]="category().color"
    >
      @switch (category().icon) {
        @case ('utensils') { <svg lucideUtensils [size]="14" [strokeWidth]="2.25" /> }
        @case ('car') { <svg lucideCar [size]="14" [strokeWidth]="2.25" /> }
        @case ('zap') { <svg lucideZap [size]="14" [strokeWidth]="2.25" /> }
        @case ('repeat') { <svg lucideRepeat [size]="14" [strokeWidth]="2.25" /> }
        @case ('music') { <svg lucideMusic [size]="14" [strokeWidth]="2.25" /> }
        @case ('heart') { <svg lucideHeart [size]="14" [strokeWidth]="2.25" /> }
        @case ('book') { <svg lucideBook [size]="14" [strokeWidth]="2.25" /> }
        @case ('shirt') { <svg lucideShirt [size]="14" [strokeWidth]="2.25" /> }
        @case ('home') { <svg lucideHome [size]="14" [strokeWidth]="2.25" /> }
        @case ('briefcase') { <svg lucideBriefcase [size]="14" [strokeWidth]="2.25" /> }
        @case ('monitor') { <svg lucideMonitor [size]="14" [strokeWidth]="2.25" /> }
        @case ('trending-up') { <svg lucideTrendingUp [size]="14" [strokeWidth]="2.25" /> }
        @case ('arrow-right-left') { <svg lucideArrowRightLeft [size]="14" [strokeWidth]="2.25" /> }
        @case ('dumbbell') { <svg lucideDumbbell [size]="14" [strokeWidth]="2.25" /> }
        @default { <svg lucideCircle [size]="14" [strokeWidth]="2.25" /> }
      }
      <span class="badge-name">{{ category().name }}</span>
    </span>
  `,
  styles: [`
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 4px 11px 4px 8px;
      border-radius: 9999px;
      border: 1px solid;
      font-size: 12px;
      font-weight: 600;
      line-height: 1.5;
      white-space: nowrap;
    }
    .badge-name {
      font-size: 12px;
    }
    svg {
      flex-shrink: 0;
    }
  `],
})
export class CategoryBadgeComponent {
  category = input.required<CategoryResponse>();

  bgColor = computed(() => hexToRgba(this.category().color, 0.22));
  borderColor = computed(() => hexToRgba(this.category().color, 0.55));
}

function hexToRgba(hex: string, alpha: number): string {
  if (!hex?.startsWith('#') || hex.length !== 7) return 'transparent';
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

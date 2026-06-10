import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { CategoryService } from './category.service';
import { CategoryRequest, CategoryResponse } from '../models/category.models';

const MOCK_CATEGORY: CategoryResponse = {
  id: 'cat-123',
  name: 'Alimentos',
  icon: 'utensils',
  color: '#10B981',
  type: 'EXPENSE',
  isDefault: true,
  estimatedAmount: null,
};

describe('CategoryService', () => {
  let service: CategoryService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [CategoryService],
    });

    service = TestBed.inject(CategoryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('se crea correctamente', () => {
    expect(service).toBeTruthy();
  });

  // ─── getCategories() ────────────────────────────────────────────────────────

  it('getCategories() hace GET a /api/categories', () => {
    service.getCategories().subscribe(cats => {
      expect(cats.length).toBe(1);
      expect(cats[0].name).toBe('Alimentos');
    });

    const req = httpMock.expectOne(r => r.url.includes('/api/categories') && r.method === 'GET');
    expect(req.request.method).toBe('GET');
    req.flush([MOCK_CATEGORY]);
  });

  // ─── createCategory() ────────────────────────────────────────────────────────

  it('createCategory() hace POST a /api/categories con el body correcto', () => {
    const request: CategoryRequest = {
      name: 'Gym',
      icon: 'dumbbell',
      color: '#EC4899',
      type: 'EXPENSE',
      estimatedAmount: null,
    };
    const created: CategoryResponse = { ...request, id: 'new-id', isDefault: false, estimatedAmount: null };

    service.createCategory(request).subscribe(res => {
      expect(res.id).toBe('new-id');
      expect(res.name).toBe('Gym');
    });

    const req = httpMock.expectOne(r => r.url.includes('/api/categories') && r.method === 'POST');
    expect(req.request.body).toEqual(request);
    req.flush(created);
  });

  // ─── updateCategory() ────────────────────────────────────────────────────────

  it('updateCategory() hace PUT a /api/categories/{id} con el body correcto', () => {
    const id = 'cat-456';
    const request: CategoryRequest = {
      name: 'Gym pro',
      icon: 'dumbbell',
      color: '#EC4899',
      type: 'EXPENSE',
      estimatedAmount: null,
    };
    const updated: CategoryResponse = { ...request, id, isDefault: false, estimatedAmount: null };

    service.updateCategory(id, request).subscribe(res => {
      expect(res.name).toBe('Gym pro');
    });

    const req = httpMock.expectOne(r => r.url.includes(`/api/categories/${id}`) && r.method === 'PUT');
    expect(req.request.body).toEqual(request);
    req.flush(updated);
  });

  // ─── deleteCategory() ────────────────────────────────────────────────────────

  it('deleteCategory() hace DELETE a /api/categories/{id}', () => {
    const id = 'cat-789';

    service.deleteCategory(id).subscribe(res => {
      expect(res).toBeNull();
    });

    const req = httpMock.expectOne(r => r.url.includes(`/api/categories/${id}`) && r.method === 'DELETE');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});

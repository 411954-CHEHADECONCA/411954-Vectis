import { TestBed } from '@angular/core/testing';
import { StorageService } from './storage.service';

describe('StorageService', () => {
  let service: StorageService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [StorageService] });
    service = TestBed.inject(StorageService);
  });

  afterEach(() => localStorage.clear());

  it('se crea correctamente', () => {
    expect(service).toBeTruthy();
  });

  it('setAccessToken / getAccessToken almacenan y recuperan el valor', () => {
    service.setAccessToken('my-access-token');
    expect(service.getAccessToken()).toBe('my-access-token');
  });

  it('getAccessToken retorna null cuando no hay token', () => {
    expect(service.getAccessToken()).toBeNull();
  });

  it('setRefreshToken / getRefreshToken almacenan y recuperan el valor', () => {
    service.setRefreshToken('my-refresh-token');
    expect(service.getRefreshToken()).toBe('my-refresh-token');
  });

  it('getRefreshToken retorna null cuando no hay token', () => {
    expect(service.getRefreshToken()).toBeNull();
  });

  it('setUser / getUser serializa y deserializa correctamente', () => {
    const user = { id: '1', email: 'a@a.com', fullName: 'User A' };
    service.setUser(user);
    expect(service.getUser()).toEqual(user);
  });

  it('getUser retorna null cuando no hay usuario', () => {
    expect(service.getUser()).toBeNull();
  });

  it('clear() elimina access token, refresh token y usuario', () => {
    service.setAccessToken('token');
    service.setRefreshToken('refresh');
    service.setUser({ id: '1', email: 'a@a.com', fullName: 'A' });

    service.clear();

    expect(service.getAccessToken()).toBeNull();
    expect(service.getRefreshToken()).toBeNull();
    expect(service.getUser()).toBeNull();
  });
});

import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { routes } from './app.routes';
import { TerminosCondicionesComponent } from './features/legal/terminos-condiciones/terminos-condiciones.component';
import { ContactoComponent } from './features/legal/contacto/contacto.component';

describe('app.routes — rutas públicas legales', () => {
  let harness: RouterTestingHarness;
  let router: Router;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });

    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
  });

  it('/terminos navega sin redirigir a /login y carga TerminosCondicionesComponent', async () => {
    const el = await harness.navigateByUrl('/terminos');

    expect(router.url).toBe('/terminos');
    expect(el).withContext('el componente activado no debe ser null').toBeTruthy();
    expect(el instanceof TerminosCondicionesComponent).toBeTrue();
    expect(harness.routeNativeElement?.textContent).toContain('Términos y Condiciones');
  });

  it('/contacto navega sin redirigir a /login y carga ContactoComponent', async () => {
    const el = await harness.navigateByUrl('/contacto');

    expect(router.url).toBe('/contacto');
    expect(el).withContext('el componente activado no debe ser null').toBeTruthy();
    expect(el instanceof ContactoComponent).toBeTrue();
    expect(harness.routeNativeElement?.textContent).toContain('Contáctanos');
  });

  it('no aplica el authGuard a las rutas legales: no se registra ningún canActivate en su config', () => {
    const terminosRoute = routes.find((r) => r.path === 'terminos');
    const contactoRoute = routes.find((r) => r.path === 'contacto');

    expect(terminosRoute).toBeTruthy();
    expect(contactoRoute).toBeTruthy();
    expect(terminosRoute?.canActivate ?? []).toEqual([]);
    expect(contactoRoute?.canActivate ?? []).toEqual([]);
  });
});

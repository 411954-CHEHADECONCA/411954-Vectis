import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LegalLayoutComponent } from '../../../shared/components/legal-layout/legal-layout.component';

interface Faq {
  readonly id: string;
  readonly question: string;
  readonly answer: string;
}

@Component({
  selector: 'app-contacto',
  standalone: true,
  imports: [RouterLink, LegalLayoutComponent],
  templateUrl: './contacto.component.html',
  styleUrl: './contacto.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContactoComponent {
  readonly contactEmail = 'chehadesantiago@gmail.com';

  private readonly openIds = signal<ReadonlySet<string>>(new Set());

  readonly faqs: readonly Faq[] = [
    {
      id: 'crear-cuenta',
      question: '¿Cómo creo mi cuenta y mi contraseña?',
      answer:
        'Ingresá a la pantalla de registro, completá tu nombre, tu correo electrónico y elegí una contraseña de al menos 8 caracteres. Tu contraseña es personal y secreta: no la compartas con nadie. Al registrarte accedés automáticamente a la aplicación.',
    },
    {
      id: 'recuperar-contrasena',
      question: 'Olvidé mi contraseña, ¿cómo recupero el acceso?',
      answer:
        'En la pantalla de inicio de sesión, tocá «¿Olvidaste tu contraseña?» e ingresá tu correo. Te enviaremos un enlace para restablecerla, válido por una hora. Si no lo recibís, revisá la carpeta de correo no deseado.',
    },
    {
      id: 'cambiar-contrasena',
      question: '¿Cómo cambio mi contraseña estando dentro de la app?',
      answer:
        'Desde Configuración, en la sección de seguridad, podés cambiar tu contraseña ingresando la actual y la nueva.',
    },
    {
      id: 'contacto-consultas',
      question: '¿A qué dirección me puedo contactar para una consulta, sugerencia o reclamo?',
      answer:
        'Escribinos a chehadesantiago@gmail.com. Tené en cuenta que la dirección noreply@vectis.app es solo para envíos automáticos y no se monitorea.',
    },
    {
      id: 'requisitos',
      question: '¿Qué necesito para empezar a usar Vectis?',
      answer:
        'Solo un dispositivo con conexión a Internet y un navegador web. Vectis funciona mejor desde computadora de escritorio. No necesitás vincular ninguna cuenta bancaria: vos cargás y actualizás tus saldos manualmente.',
    },
    {
      id: 'como-funciona',
      question: '¿Cómo funciona Vectis?',
      answer:
        'Vectis consolida tus cuentas, tarjetas e inversiones en pesos y dólares en una sola vista. Registrás tus movimientos de ingreso y egreso, definís presupuestos por categoría y, a fin de mes, cerrás tu cashflow para ver cómo evolucionó tu patrimonio. Toda la información la cargás y controlás vos.',
    },
    {
      id: 'sin-vinculo-bancario',
      question: '¿Vectis se conecta a mi banco o mueve mi dinero?',
      answer:
        'No. Vectis es una herramienta de seguimiento: no se conecta a cuentas bancarias, no mueve dinero real ni ejecuta operaciones. Los saldos y movimientos son anotaciones que ingresás vos.',
    },
    {
      id: 'cotizaciones',
      question: '¿Las cotizaciones y precios son en tiempo real?',
      answer:
        'Las cotizaciones del dólar, los precios de instrumentos y los índices de inflación provienen de fuentes de terceros y se muestran con fines informativos. Pueden tener demoras o diferencias, y no constituyen asesoramiento financiero.',
    },
    {
      id: 'costo',
      question: '¿Tiene algún costo? ¿Qué medios de pago aceptan?',
      answer:
        'Vectis es gratuito por tratarse de un proyecto académico. No cobramos comisiones ni suscripciones y no se procesan pagos dentro de la aplicación.',
    },
    {
      id: 'baja-cuenta',
      question: '¿Cómo doy de baja mi cuenta?',
      answer:
        'Podés dejar de usar el Servicio cuando quieras. Para solicitar la eliminación de tu cuenta y tus datos, escribinos a chehadesantiago@gmail.com.',
    },
  ];

  isOpen(id: string): boolean {
    return this.openIds().has(id);
  }

  toggle(id: string): void {
    this.openIds.update((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }
}

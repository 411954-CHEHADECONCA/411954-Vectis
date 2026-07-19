import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LegalLayoutComponent } from '../../../shared/components/legal-layout/legal-layout.component';

interface LegalSection {
  readonly id: string;
  readonly title: string;
  readonly paragraphs: readonly string[];
}

@Component({
  selector: 'app-terminos-condiciones',
  standalone: true,
  imports: [RouterLink, LegalLayoutComponent],
  templateUrl: './terminos-condiciones.component.html',
  styleUrl: './terminos-condiciones.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TerminosCondicionesComponent {
  readonly lastUpdated = '12 de julio de 2026';

  readonly sections: readonly LegalSection[] = [
    {
      id: 'generalidades',
      title: 'Generalidades',
      paragraphs: [
        'Bienvenido a Vectis, una herramienta de gestión de finanzas personales bimonetaria (pesos argentinos y dólares estadounidenses). Al acceder y utilizar Vectis (el «Servicio») expresás tu consentimiento y aceptación de estos Términos y Condiciones. Si no estás de acuerdo con ellos, no utilices el Servicio.',
        'Vectis es un proyecto académico desarrollado en el marco de la Práctica Profesional Supervisada de la Tecnicatura Universitaria en Programación de la Universidad Tecnológica Nacional (UTN). No es un producto comercial, ni una entidad financiera, bancaria o cambiaria autorizada.',
        'Al usar el Servicio aceptás las modalidades operativas vigentes descriptas más adelante, y las que se habiliten en el futuro. Podemos actualizar estos Términos; los cambios sustanciales se comunicarán a través de la aplicación.',
      ],
    },
    {
      id: 'permiso-uso',
      title: 'Permiso para utilizar el Servicio',
      paragraphs: [
        'Te otorgamos un permiso personal, limitado, no exclusivo e intransferible para acceder y utilizar Vectis con fines de organización de tus finanzas personales. Sos responsable de mantener la confidencialidad de tu contraseña y de toda la actividad realizada desde tu cuenta.',
        'No está permitido revender, redistribuir, realizar ingeniería inversa ni utilizar el Servicio con fines ilícitos o que puedan dañar su funcionamiento.',
      ],
    },
    {
      id: 'reserva-derechos',
      title: 'Reserva de derechos',
      paragraphs: [
        'Todos los derechos no otorgados expresamente en estos Términos quedan reservados a los autores de Vectis. El permiso de uso no implica cesión de titularidad alguna sobre el software, la marca «Vectis» ni sus contenidos.',
      ],
    },
    {
      id: 'descripcion-servicio',
      title: 'Descripción del Servicio',
      paragraphs: [
        'Vectis es una aplicación web que te permite consolidar y hacer seguimiento manual de tu patrimonio en ARS y USD. El Servicio no se conecta a tus cuentas bancarias, no mueve dinero real ni ejecuta operaciones financieras; toda la información de saldos y movimientos es ingresada y mantenida por vos.',
        'El Servicio comprende los siguientes módulos: Cuentas (saldos por cuenta y moneda), Movimientos (ingresos, egresos, movimientos recurrentes y transferencias entre cuentas), Cashflow mensual (flujo de fondos, presupuestos por categoría y cierre de mes), Tarjetas (cuotas y proyección de consumos), Inversiones (seguimiento de FCI, Plazos Fijos, Letras, Bonos y Obligaciones Negociables) y Configuración (categorías y preferencias).',
        'Las cotizaciones de dólar, precios de instrumentos financieros e índices de inflación provienen de fuentes de terceros (por ejemplo Portfolio Personal Inversiones, dolarapi.com y argentinadatos.com), se muestran con fines meramente informativos, pueden presentar demoras o inexactitudes, y no constituyen asesoramiento financiero, de inversión, legal ni impositivo.',
      ],
    },
    {
      id: 'operaciones-habilitadas',
      title: 'Operaciones habilitadas',
      paragraphs: [
        'Las operaciones habilitadas son las funciones disponibles dentro de la aplicación para registrar y visualizar tu información financiera: alta y edición de cuentas, carga de movimientos e ingresos/egresos, definición de presupuestos, registro de tarjetas y cuotas, y seguimiento de inversiones. Estas operaciones pueden ampliarse o restringirse en futuras versiones del Servicio.',
        'Ninguna operación dentro de Vectis implica la transferencia, custodia ni administración de fondos reales.',
      ],
    },
    {
      id: 'transacciones',
      title: 'Transacciones y acceso',
      paragraphs: [
        'Para operar el Servicio se requiere una cuenta de usuario. El registro solicita únicamente tu nombre, tu correo electrónico y una contraseña; no se solicita número de documento ni datos bancarios. Podés acceder desde cualquier dispositivo con conexión a Internet.',
        'Tu contraseña es personal, secreta e intransferible. Vectis nunca te pedirá tu contraseña por correo electrónico ni por ningún otro medio fuera de la propia aplicación. Asumís las consecuencias derivadas de su divulgación a terceros.',
        'Los «movimientos» registrados en Vectis son anotaciones informativas cargadas por vos y no representan operaciones con efecto sobre dinero real.',
      ],
    },
    {
      id: 'costo',
      title: 'Costo del Servicio',
      paragraphs: [
        'Vectis se ofrece de forma gratuita en su carácter de proyecto académico. No se cobran comisiones, cargos de mantenimiento ni suscripciones, y no se procesan pagos ni se aceptan medios de pago dentro de la aplicación. En caso de que a futuro se incorporara algún costo, se comunicaría con al menos 60 días de antelación.',
      ],
    },
    {
      id: 'vigencia',
      title: 'Vigencia y baja',
      paragraphs: [
        'Podés dejar de utilizar el Servicio y solicitar la baja de tu cuenta en cualquier momento, sin más responsabilidad que la derivada del uso realizado hasta ese momento.',
        'Podremos suspender o dar de baja el acceso al Servicio ante el incumplimiento de estos Términos, o discontinuar el Servicio por tratarse de un proyecto académico, procurando dar aviso previo razonable a través de la aplicación.',
      ],
    },
    {
      id: 'validez',
      title: 'Validez de operaciones y notificaciones',
      paragraphs: [
        'Los registros generados por la aplicación constituyen prueba suficiente de las operaciones cursadas por este canal. Las notificaciones enviadas por correo electrónico o mostradas dentro de la aplicación tienen la misma validez que una notificación por medio escrito.',
      ],
    },
    {
      id: 'propiedad-intelectual',
      title: 'Propiedad intelectual',
      paragraphs: [
        'El software y sus contenidos están protegidos por la Ley 11.723 de Propiedad Intelectual de la República Argentina, que ampara los derechos de autor sobre obras literarias, artísticas y científicas, incluidos los programas de computación.',
        'Material desarrollado para la Tecnicatura Universitaria en Programación, Universidad Tecnológica Nacional — Facultad Regional Córdoba.',
      ],
    },
    {
      id: 'privacidad',
      title: 'Privacidad de la información',
      paragraphs: [
        'Para utilizar Vectis proporcionás ciertos datos personales (nombre y correo electrónico) e información financiera que cargás vos mismo. Las contraseñas se almacenan siempre cifradas (hash BCrypt), nunca en texto plano.',
        'Tu información se procesa y almacena aplicando medidas de seguridad razonables. No comercializamos tus datos personales ni los cedemos a terceros, salvo obligación legal. Ante cualquier consulta sobre tus datos podés escribirnos a chehadesantiago@gmail.com.',
      ],
    },
  ];
}

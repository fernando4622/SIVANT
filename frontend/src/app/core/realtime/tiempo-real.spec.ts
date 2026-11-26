import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { StompConfig } from '@stomp/stompjs';
import { Perfil, Sesion } from '../auth/sesion';
import { CREAR_CLIENTE, Evento, TiempoReal } from './tiempo-real';

interface ClienteFalso {
  config: StompConfig;
  destinos: string[];
  recibir: (cuerpo: object) => void;
  activate: ReturnType<typeof vi.fn>;
  deactivate: ReturnType<typeof vi.fn>;
}

describe('Tiempo real', () => {
  const perfil = signal<Perfil | null>(null);
  const comprobar = vi.fn();
  let clientes: ClienteFalso[];
  let servicio: TiempoReal;

  beforeEach(() => {
    perfil.set(null);
    comprobar.mockReset();
    clientes = [];
    TestBed.configureTestingModule({
      providers: [
        { provide: Sesion, useValue: { perfil, comprobar } },
        {
          provide: CREAR_CLIENTE,
          useValue: (config: StompConfig) => {
            const manejadores: ((m: { body: string }) => void)[] = [];
            const cliente = {
              config,
              destinos: [] as string[],
              recibir: (cuerpo: object) => manejadores.forEach((m) => m({ body: JSON.stringify(cuerpo) })),
              activate: vi.fn(),
              deactivate: vi.fn(() => Promise.resolve()),
              subscribe: (destino: string, manejador: (m: { body: string }) => void) => {
                cliente.destinos.push(destino);
                manejadores.push(manejador);
              },
            };
            clientes.push(cliente);
            return cliente;
          },
        },
      ],
    });
    servicio = TestBed.inject(TiempoReal);
    TestBed.tick();
  });

  function entrar(rol: Perfil['rol'], id = '1') {
    perfil.set({ id, nombre: 'A', rol });
    TestBed.tick();
    const cliente = clientes.at(-1)!;
    cliente.config.onConnect!({} as never);
    return cliente;
  }

  const evento = (eventoId: string, tipo = 'COMANDA_ENVIADA') =>
    ({ eventoId, tipo, recursoId: '5', version: 0, fechaHora: 'x' }) as Evento;

  it('CP-F38 sin sesión no se conecta; cada rol se suscribe solo a lo suyo', () => {
    expect(clientes).toHaveLength(0);
    expect(entrar('COCINERO').destinos).toEqual(['/user/queue/notificaciones', '/topic/cocina']);
    expect(entrar('MESERO', '2').destinos).toEqual(['/user/queue/notificaciones']);
    expect(entrar('ALMACENISTA', '3').destinos).toEqual(['/user/queue/notificaciones', '/topic/inventario']);
  });

  it('CP-F39 ignora eventos repetidos y filtra por tipo', () => {
    const todos: (Evento | null)[] = [];
    const listas: (Evento | null)[] = [];
    servicio.cambios().subscribe((e) => todos.push(e));
    servicio.cambios('COMANDA_LISTA').subscribe((e) => listas.push(e));
    const cliente = entrar('COCINERO');
    cliente.recibir(evento('a'));
    todos.length = 0;
    cliente.recibir(evento('a'));
    expect(todos).toHaveLength(0);
    cliente.recibir(evento('b', 'COMANDA_LISTA'));
    expect(listas.map((e) => e?.eventoId)).toEqual(['b']);
  });

  it('CP-F40 al reconectar avisa para recuperar lo perdido; la primera conexión no', () => {
    const cambios: (Evento | null)[] = [];
    servicio.cambios('COMANDA_LISTA').subscribe((e) => cambios.push(e));
    const cliente = entrar('MESERO');
    expect(cambios).toEqual([]);
    cliente.config.onWebSocketClose!({} as never);
    expect(servicio.conectado()).toBe(false);
    expect(comprobar).toHaveBeenCalled();
    cliente.config.onConnect!({} as never);
    expect(cambios).toEqual([null]);
    expect(servicio.conectado()).toBe(true);
  });

  it('CP-F41 al cerrar sesión se desconecta; una actualización del perfil no reconecta', () => {
    const cliente = entrar('MESERO');
    perfil.set({ id: '1', nombre: 'Otro nombre', rol: 'MESERO' });
    TestBed.tick();
    expect(clientes).toHaveLength(1);
    perfil.set(null);
    TestBed.tick();
    expect(cliente.deactivate).toHaveBeenCalled();
    expect(servicio.conectado()).toBe(false);
  });
});

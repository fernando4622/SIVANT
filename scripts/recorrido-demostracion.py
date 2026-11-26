"""Recorrido de demostración por la API, con los cinco roles (docs/demostracion.md).

Requiere la API con el perfil dev y Angular en el puerto 4200. Lee SIVANT_DEV_CONTRASENA del entorno o de .env.
Crea datos nuevos en cada ejecución.

    python scripts/recorrido-demostracion.py
"""
import json
import os
import sys
from decimal import Decimal
import uuid
import http.cookiejar
import urllib.request
import urllib.error

BASE = 'http://localhost:4200/api/v1'
def clave_de_prueba():
    if os.environ.get('SIVANT_DEV_CONTRASENA'):
        return os.environ['SIVANT_DEV_CONTRASENA']
    ruta = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '.env')
    with open(ruta, encoding='utf8') as env:
        for linea in env:
            if linea.startswith('SIVANT_DEV_CONTRASENA='):
                return linea.split('=', 1)[1].strip()
    sys.exit('Falta SIVANT_DEV_CONTRASENA en el entorno o en .env')


CLAVE = clave_de_prueba()
resultados = []


class Cliente:
    def __init__(self):
        self.cookies = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        self.pedir('GET', '/auth/csrf')

    def pedir(self, metodo, ruta, cuerpo=None, clave=False):
        datos = None if cuerpo is None else json.dumps(cuerpo).encode()
        req = urllib.request.Request(BASE + ruta, data=datos, method=metodo)
        req.add_header('Content-Type', 'application/json')
        xsrf = next((c.value for c in self.cookies if c.name == 'XSRF-TOKEN'), None)
        if xsrf:
            req.add_header('X-XSRF-TOKEN', xsrf)
        if clave:
            req.add_header('Idempotency-Key', str(uuid.uuid4()))
        try:
            with self.op.open(req) as r:
                texto = r.read().decode()
                return r.status, (json.loads(texto) if texto else None)
        except urllib.error.HTTPError as e:
            texto = e.read().decode()
            return e.code, (json.loads(texto) if texto.startswith('{') else None)

    def entrar(self, correo, contrasena=CLAVE):
        return self.pedir('POST', '/auth/login', {'correo': correo, 'contrasena': contrasena})[0]


def paso(texto, condicion):
    resultados.append((texto, bool(condicion)))
    print(('OK   ' if condicion else 'FALLA') + ' ' + texto)


def sesion(rol):
    c = Cliente()
    assert c.entrar(rol + '@sivant.test') == 200, rol
    return c


admin, mesero, cajero, cocinero, almacen = (sesion(r) for r in ('admin', 'mesero', 'cajero', 'cocinero', 'almacenista'))

# 1 a 5 · Empleados y permisos
e, lista = admin.pedir('GET', '/empleados?rol=MESERO&estado=ACTIVO')
paso('1. El administrador filtra empleados por rol y estado', e == 200 and all(x['rol'] == 'MESERO' for x in lista['contenido']))
alta = {'nombreCompleto': 'Empleado Demo', 'correo': 'MESERO@sivant.test', 'contrasena': 'Demo.Clon.2026', 'rol': 'CAJERO'}
e, p = admin.pedir('POST', '/empleados', alta)
paso('2. Un correo ya usado, en mayúsculas, se rechaza', e == 409 and p['codigo'] == 'CORREO_DUPLICADO')
CORREO_DEMO = f'demo-{uuid.uuid4().hex[:8]}@sivant.test'
alta['correo'] = CORREO_DEMO
e, nuevo = admin.pedir('POST', '/empleados', alta)
paso('2. Con otro correo se registra', e == 201)
demo = Cliente()
paso('3. El empleado nuevo entra y no ve empleados', demo.entrar(CORREO_DEMO, 'Demo.Clon.2026') == 200
     and demo.pedir('GET', '/empleados')[0] == 403)
e, _ = admin.pedir('POST', f"/empleados/{nuevo['id']}/desactivacion", {'confirmado': True})
paso('4. Desactivado, pierde la sesión y no vuelve a entrar', e == 200 and demo.pedir('GET', '/auth/me')[0] == 401
     and Cliente().entrar(CORREO_DEMO, 'Demo.Clon.2026') == 401)
paso('5. El mesero recibe 403 en empleados', mesero.pedir('GET', '/empleados')[0] == 403)

# 6 y 7 · Inventario
_, ex = almacen.pedir('GET', '/inventario/existencias?q=Agua')
agua = ex['contenido'][0]
grande = str(int(float(agua['cantidadActual'])) + 1000)
e, p = almacen.pedir('POST', f"/inventario/existencias/{agua['id']}/movimientos", {'tipo': 'SALIDA_MANUAL', 'cantidad': grande}, True)
paso('6. Una salida mayor que la existencia se rechaza', e == 409)
e, _ = almacen.pedir('POST', f"/inventario/existencias/{agua['id']}/movimientos", {'tipo': 'ENTRADA_MANUAL', 'cantidad': '10'}, True)
e2, sol = almacen.pedir('POST', '/inventario/ajustes', {'existenciaId': agua['id'], 'cantidadSolicitada': '100', 'justificacion': 'Conteo de demostración'}, True)
paso('6. Se registra una entrada y se solicita un ajuste', e == 201 and e2 == 201)
e, _ = admin.pedir('POST', f"/inventario/ajustes/{sol['id']}/resolucion", {'decision': 'AUTORIZADA'}, True)
_, mov = admin.pedir('GET', '/inventario/movimientos?tipo=AJUSTE')
paso('7. El administrador lo autoriza y aparece en movimientos', e == 200 and mov['totalElementos'] >= 1)

# 8 · Productos
NOMBRE_PREPARADO = f'Demo preparado {uuid.uuid4().hex[:4]}'
NOMBRE_REVENTA = f'Demo reventa {uuid.uuid4().hex[:4]}'
_, insumos = admin.pedir('GET', '/insumos')
e, prep = admin.pedir('POST', '/productos', {'nombre': NOMBRE_PREPARADO, 'descripcion': 'x', 'precioVenta': '30.00', 'tipo': 'PREPARADO',
                                              'unidadMedida': 'PIEZA', 'receta': [{'insumoId': insumos[0]['id'], 'cantidadRequerida': '1'}]})
e2, rev = admin.pedir('POST', '/productos', {'nombre': NOMBRE_REVENTA, 'descripcion': 'x', 'precioVenta': '15.00', 'tipo': 'REVENTA',
                                             'unidadMedida': 'PIEZA', 'nivelMinimo': '0'})
e3, _ = admin.pedir('PATCH', f"/productos/{prep['id']}", {'precioVenta': '32.00'})
e4, _ = admin.pedir('POST', f"/productos/{rev['id']}/desactivacion", {'confirmado': True})
_, catalogo = mesero.pedir('GET', '/productos?size=100')
nombres = [p['nombre'] for p in catalogo['contenido']]
paso('8. Alta, cambio de precio y desactivación; el mesero solo ve los activos',
     (e, e2, e3, e4) == (201, 201, 200, 200) and NOMBRE_PREPARADO in nombres and NOMBRE_REVENTA not in nombres)

# 9 a 12 · Cuenta, cocina y servicio
_, mesas = mesero.pedir('GET', '/mesas')
libres = [m['id'] for m in mesas if m['disponible']]
e, cuenta = mesero.pedir('POST', '/cuentas', {'mesaId': libres[0]}, True)
e2, com = mesero.pedir('POST', f"/cuentas/{cuenta['id']}/comandas", {'detalles': [{'productoId': prep['id'], 'cantidad': 2}]}, True)
_, otra = mesero.pedir('POST', f"/cuentas/{cuenta['id']}/comandas", {'detalles': [{'productoId': prep['id'], 'cantidad': 1}]}, True)
paso('9. El mesero abre una mesa y envía comandas', e == 201 and e2 == 201)
_, cola = cocinero.pedir('GET', '/cocina/cola')
e, _ = cocinero.pedir('POST', f"/comandas/{com['id']}/preparacion", {'version': 0})
e2, editada = mesero.pedir('PUT', f"/comandas/{com['id']}/detalles", {'version': 1, 'detalles': [{'detalleId': com['detalles'][0]['id'], 'cantidad': 3}]})
_, avisos_cocina = cocinero.pedir('GET', '/notificaciones')
paso('11. Modificar en preparación avisa a cocina', e == 200 and e2 == 200
     and any(a['tipo'] == 'MODIFICACION_COMANDA' for a in avisos_cocina['contenido']))
e, _ = cocinero.pedir('POST', f"/comandas/{com['id']}/lista", {'version': 2})
_, avisos = mesero.pedir('GET', '/notificaciones')
paso('10. Cocina la ve en la cola, la prepara y avisa al mesero que está lista', len(cola) >= 2 and e == 200
     and any(a['tipo'] == 'COMANDA_LISTA' for a in avisos['contenido']))
e, _ = cajero.pedir('POST', f"/comandas/{otra['id']}/cancelacion", {'version': 0, 'justificacion': 'Demostración'}, True)
paso('11. El cajero cancela una comanda con justificación', e == 200)
_, antes = almacen.pedir('GET', '/insumos')
e, servida = mesero.pedir('POST', f"/comandas/{com['id']}/servicio", {'version': 3}, True)
paso('12. El mesero la sirve y se descuenta el inventario', e == 200 and len(servida['consumo']) == 1)

# 13 · Cobro con tarjeta
REF = 'DEMO-' + uuid.uuid4().hex[:8].upper()
_, c = mesero.pedir('GET', f"/cuentas/{cuenta['id']}")
e, cobro = mesero.pedir('POST', f"/cuentas/{cuenta['id']}/cobro", {'versionCuenta': c['version'], 'metodo': 'TARJETA',
                                                                   'referenciaExterna': REF, 'totalEsperado': c['total']}, True)
_, mesas2 = mesero.pedir('GET', '/mesas')
paso('13. Se cobra con tarjeta, la cuenta se cierra y la mesa queda libre', e == 201 and cobro['cuenta']['estado'] == 'CERRADA'
     and any(m['id'] == libres[0] and m['disponible'] for m in mesas2))

# 14 · Ventas
_, ventas = cajero.pedir('GET', '/ventas?metodo=TARJETA')
e, cancelada = admin.pedir('POST', f"/ventas/{cobro['venta']['id']}/cancelacion", {'confirmado': True, 'justificacion': 'Demostración',
                                                                                    'referenciaReversion': 'REV-' + REF}, True)
paso('14. El cajero consulta la venta y el administrador la cancela', ventas['totalElementos'] >= 1 and e == 200
     and cancelada['estado'] == 'CANCELADA')

# 15 y 16 · Cobro en efectivo y cambio en caja
e, cuenta2 = mesero.pedir('POST', '/cuentas', {'mesaId': libres[0]}, True)
_, com2 = mesero.pedir('POST', f"/cuentas/{cuenta2['id']}/comandas", {'detalles': [{'productoId': prep['id'], 'cantidad': 1}]}, True)
cocinero.pedir('POST', f"/comandas/{com2['id']}/preparacion", {'version': 0})
cocinero.pedir('POST', f"/comandas/{com2['id']}/lista", {'version': 1})
mesero.pedir('POST', f"/comandas/{com2['id']}/servicio", {'version': 2}, True)
_, c2 = mesero.pedir('GET', f"/cuentas/{cuenta2['id']}")
e3, cobro2 = mesero.pedir('POST', f"/cuentas/{cuenta2['id']}/cobro", {'versionCuenta': c2['version'], 'metodo': 'EFECTIVO', 'totalEsperado': c2['total']}, True)
paso('15. El mesero cobra en efectivo sin fondo previo', e3 == 201)
_, pendientes = cajero.pedir('GET', '/caja/pagos-efectivo-pendientes')
pendientes = [x for x in pendientes if x['id'] == cobro2['pago']['id']]
recibido = str(Decimal(c2['total']) + 50)
e, entrega = cajero.pedir('POST', '/caja/entregas-efectivo', {'meseroId': pendientes[0]['meseroId'],
                                                              'pagos': [{'pagoId': pendientes[0]['id'], 'montoRecibido': recibido}]}, True)
paso('16. El cajero recibe el efectivo y da el cambio', len(pendientes) == 1 and e == 201
     and Decimal(entrega['cambio']) == 50)

fallas = [t for t, ok in resultados if not ok]
print(f'\n{len(resultados) - len(fallas)} de {len(resultados)} pasos correctos')
sys.exit(1 if fallas else 0)

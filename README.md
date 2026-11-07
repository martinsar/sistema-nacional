# Sistema Nacional API

API REST del Sistema Nacional de RescueSync (TP DSSD 2026, Grupo 18). Gestiona las ONGs y sus recursos, valida las ofertas que llegan desde el proceso de Bonita y permite **comprometer** y **liberar** recursos para una convocatoria.

- Spring Boot 4.1.1, Java 21, PostgreSQL.
- Autenticación con JWT (HS256, vence a la hora).
- Documentación interactiva: `/swagger-ui.html` (botón **Authorize** para pegar el token).
- Colección de Postman lista para ejecutar: [`docs/postman`](docs/postman).

API desplegada en Render: <https://sistema-nacional-e2uv.onrender.com> (Swagger: <https://sistema-nacional-e2uv.onrender.com/swagger-ui/index.html>). En el plan gratuito el servicio se duerme por inactividad: el primer pedido puede tardar cerca de un minuto.

## 1. Cómo autenticarse

Todos los endpoints, salvo `/` y `/api/auth/*`, exigen el header `Authorization: Bearer <token>`.

**Registrar un usuario** (solo la primera vez; la contraseña debe tener al menos 8 caracteres):

```http
POST /api/auth/register
Content-Type: application/json

{ "username": "ayudante", "password": "clave-de-prueba-123" }
```

Respuesta `201`:

```json
{ "username": "ayudante" }
```

**Login:**

```http
POST /api/auth/login
Content-Type: application/json

{ "username": "ayudante", "password": "clave-de-prueba-123" }
```

Respuesta `200`:

```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer", "expiresIn": 3600 }
```

Después se envía `accessToken` en cada pedido:

```http
GET /api/test/protected
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

| Código | Cuándo |
|---|---|
| 201 | Usuario creado |
| 400 | Usuario vacío o contraseña de menos de 8 caracteres |
| 401 | Credenciales incorrectas, o token ausente, inválido o vencido |
| 409 | El usuario ya existe |

## 2. Formato de los errores

Cuando un servicio rechaza un pedido, responde con el código HTTP y un cuerpo con el código y el mensaje:

```json
{ "codigo": 409, "mensaje": "No hay disponibilidad suficiente de 'Alimentos' en la ONG 'Cáritas': se pidieron 500 y hay 70 disponibles. No se comprometió ninguna unidad" }
```

## 3. Servicios

### ONGs

Cada ONG tiene un nombre, la lista de **tipos de recurso** que está habilitada a ofrecer y un `codigo_ong` único y estable. El `id` sigue siendo una clave interna de esta base; para referenciar una ONG entre la app local y el Sistema Nacional se debe usar `codigo_ong`. Si la app local ya asignó el código, puede enviarlo al crear la ONG en el Sistema Nacional; si no, el Sistema Nacional genera uno. El código se devuelve al crear o consultar la ONG y no cambia al modificar su nombre.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/ongs` | Lista las ONGs |
| GET | `/api/ongs/codigo/{codigo_ong}` | Devuelve una ONG usando el código estable compartido (recomendado entre sistemas) |
| GET | `/api/ongs/{id}` | Devuelve una ONG por su ID numérico interno (ruta heredada; no usar para integración entre sistemas) |
| POST | `/api/ongs` | Crea una ONG (201) |
| PUT | `/api/ongs/codigo/{codigo_ong}` | Modifica nombre y tipos usando el código estable |
| DELETE | `/api/ongs/codigo/{codigo_ong}` | Elimina usando el código estable (409 si tiene recursos) |
| PUT / DELETE | `/api/ongs/{id}` | Rutas heredadas por ID numérico interno; el ID no se comparte entre sistemas |

```json
{ "nombre": "Cáritas", "tiposRecursos": ["Alimentos", "Medicamentos"] }
```

El nombre es obligatorio (hasta 150 caracteres) y debe haber al menos un tipo de recurso; si no, responde 400.
También se puede incluir `codigo_ong` en el pedido si la app local ya asignó el identificador compartido. El código es único y no se puede cambiar al actualizar la ONG. Las ONGs existentes reciben un código al iniciar la aplicación.

La respuesta incluye el identificador compartido, por ejemplo:

```json
{ "id": 1, "codigo_ong": "ONG-550e8400-e29b-41d4-a716-446655440000", "nombre": "Cáritas",
  "tiposRecursos": ["Alimentos", "Medicamentos"] }
```

### Recursos

Un recurso es un stock de un tipo que pertenece a una ONG. La cantidad **disponible** es `cantidadTotal - cantidadBloqueada`.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/recursos` | Lista los recursos |
| GET | `/api/recursos/{id}` | Devuelve un recurso |
| POST | `/api/recursos` | Crea un recurso (201) |
| PUT | `/api/recursos/{id}` | Modifica un recurso |
| DELETE | `/api/recursos/{id}` | Elimina un recurso (409 si tiene adjudicaciones registradas) |

```json
{ "id": 8, "tipo": "Alimentos", "cantidadTotal": 100, "cantidadBloqueada": 0,
  "ongId": 1, "codigo_ong": "ONG-550e8400-e29b-41d4-a716-446655440000" }
```

Se recomienda enviar `codigo_ong` al asociar un recurso a una ONG entre sistemas. `ongId` continúa aceptándose por compatibilidad para clientes que operan dentro de la base del Sistema Nacional. La respuesta incluye ambos identificadores. Responde 400 si faltan datos o si `cantidadBloqueada` es mayor que `cantidadTotal`, y 404 si la ONG no existe.

### Validar ofertas

`POST /api/ofertas/validar` (alias: `/api/validar/recursos`)

Recibe una lista de ofertas y completa `habilitado_flag` en cada una. La app debe identificar la ONG con `codigo_ong`, que permanece igual en ambos sistemas aunque sus IDs locales sean distintos. `organizacion_nombre` se acepta temporalmente para compatibilidad con clientes anteriores, pero se recomienda migrar al código. La oferta queda habilitada (`true`) si el `recurso_tipo` coincide, sin importar mayúsculas, con alguno de los tipos asociados a la ONG; si la ONG no existe o el tipo no coincide, queda en `false`.

Pedido:

```json
[
  { "codigo_ong": "ONG-550e8400-e29b-41d4-a716-446655440000", "recurso_tipo": "Alimentos", "recurso_cantidad": 30 },
  { "codigo_ong": "ONG-550e8400-e29b-41d4-a716-446655440000", "recurso_tipo": "Maquinaria", "recurso_cantidad": 1 }
]
```

Respuesta `200` (la misma lista con el resultado):

```json
[
  { "codigo_ong": "ONG-550e8400-e29b-41d4-a716-446655440000", "recurso_tipo": "Alimentos", "recurso_cantidad": 30, "habilitado_flag": true },
  { "codigo_ong": "ONG-550e8400-e29b-41d4-a716-446655440000", "recurso_tipo": "Maquinaria", "recurso_cantidad": 1, "habilitado_flag": false }
]
```

### Comprometer recursos

`POST /api/recursos/comprometer` (alias: `/api/validar/coincidencias`)

Compromete una cantidad de recursos de una ONG para una convocatoria. **Se compromete todo lo pedido o nada**: nunca quedan recursos comprometidos a medias.

1. Busca todos los recursos de la ONG identificada por `codigo_ong` con ese tipo (sin distinguir mayúsculas), ordenados por id.
2. Los **bloquea en la base** (bloqueo pesimista, `SELECT ... FOR UPDATE`), de modo que dos pedidos simultáneos no pueden comprometer la misma unidad.
3. Verifica que la suma de lo disponible alcance. Si no alcanza, rechaza el pedido sin modificar nada.
4. Reparte la cantidad entre los recursos, tomando de cada uno lo que tenga disponible, y la suma a `cantidadBloqueada`.
5. Registra una **adjudicación** (convocatoria, ONG, recurso y cantidad bloqueada) por cada recurso afectado, para poder liberar después ese recurso específico de esa ONG.

Pedido:

```json
{ "tipo_recurso": "Alimentos", "codigo_ong": "ONG-550e8400-e29b-41d4-a716-446655440000", "cantidad": 30, "id_convocatoria": 5 }
```

`nombre_ong` se acepta temporalmente por compatibilidad; los clientes nuevos deben enviar `codigo_ong`.

Respuesta `200`:

```json
{
  "idConvocatoria": 5,
  "cantidadComprometida": 30,
  "mensaje": "Recursos comprometidos exitosamente",
  "recursosCoincidentes": [
    { "id": 1, "tipo": "Alimentos", "cantidadTotal": 100, "cantidadBloqueada": 30,
      "cantidadDisponible": 70, "ongId": 1, "codigo_ong": "ONG-550e8400-e29b-41d4-a716-446655440000" }
  ]
}
```

| Código | Cuándo |
|---|---|
| 200 | Se comprometió la cantidad completa |
| 400 | Falta `tipo_recurso`, `codigo_ong` (o `nombre_ong` legado), `id_convocatoria` o `cantidad`, o la cantidad no es mayor a cero |
| 404 | La ONG no existe o no tiene recursos de ese tipo |
| 409 | No hay disponibilidad suficiente: no se comprometió ninguna unidad |

### Liberar recursos

`POST /api/convocatorias/{id}/recursos/liberar` (alias: `/api/validar/liberar`)

Libera todo lo que se comprometió para una convocatoria. Solo hace falta enviar su id: el servicio busca las adjudicaciones pendientes, bloquea los recursos involucrados y devuelve a cada uno la cantidad que se le había bloqueado. Cada adjudicación se libera una sola vez.

Si algún recurso adjudicado ya no pertenece a su ONG, no se puede liberar, pero **igualmente se liberan todos los demás** para que no queden bloqueados. En ese caso la respuesta es exitosa y el mensaje lo indica, sin detallar qué ONGs.

Pedido:

```json
{ "id_convocatoria": 5 }
```

Respuesta `200`:

```json
{
  "idConvocatoria": 5,
  "mensaje": "Recursos liberados exitosamente",
  "adjudicacionesLiberadas": 1,
  "cantidadTotalLiberada": 30,
  "recursosLiberados": [
    { "recursoId": 1, "tipo": "Alimentos", "cantidadLiberada": 30, "cantidadBloqueada": 0,
      "cantidadDisponible": 100, "ongId": 1, "codigo_ong": "ONG-550e8400-e29b-41d4-a716-446655440000" }
  ]
}
```

Cuando hay recursos que no se pudieron liberar, el mensaje es: `Recursos liberados exitosamente. Algunos de los recursos adjudicados ya no existen en la base de ciertas ONGs y no se pudieron liberar`.

| Código | Cuándo |
|---|---|
| 200 | Recursos liberados (revisar `mensaje`) |
| 400 | Falta `id_convocatoria` |
| 404 | La convocatoria no tiene adjudicaciones pendientes (nunca comprometió recursos, o ya se liberó) |

## 4. Flujo típico

1. Login y obtención del token.
2. `POST /api/ofertas/validar` con las ofertas recibidas: se descartan las que vuelven con `habilitado_flag: false`.
3. `POST /api/recursos/comprometer` por cada oferta aceptada, con el `id_convocatoria`: si responde 200 quedó todo comprometido; si responde 409 no se comprometió nada.
4. Al terminar o cancelar la convocatoria, `POST /api/convocatorias/{id}/recursos/liberar` con su `id_convocatoria`.

## 5. Cómo levantarla

Con Docker (levanta también PostgreSQL):

```bash
cp .env.example .env
docker compose up --build
```

El archivo `.env` define `POSTGRES_PASSWORD`, `JWT_SECRET` (Base64 de al menos 32 bytes) y `APP_PORT`. Las pruebas automáticas usan H2 en memoria:

```bash
./mvnw test
```

## 6. Preparación de los datos

Antes de probar el compromiso y la liberación hace falta tener cargado un usuario, al menos una ONG y recursos. Se pueden crear con los mismos servicios de la API, en este orden:

1. **Usuario y token:** registrar un usuario y hacer login (sección 1).
2. **Crear una ONG** con los tipos de recurso que puede ofrecer:

   ```http
   POST /api/ongs
   Authorization: Bearer <token>

   { "nombre": "Cáritas", "tiposRecursos": ["Alimentos", "Medicamentos"] }
   ```

   Anotar el `codigo_ong` que devuelve; ese es el identificador que se comparte con la app local. El `id` solo se usa internamente en esta base.
3. **Crear los recursos** de esa ONG, enviando `codigo_ong`:

   ```http
   POST /api/recursos
   Authorization: Bearer <token>

   { "tipo": "Alimentos", "cantidadTotal": 100, "cantidadBloqueada": 0, "codigo_ong": "ONG-550e8400-e29b-41d4-a716-446655440000" }
   ```

   ```http
   POST /api/recursos
   Authorization: Bearer <token>

   { "tipo": "Medicamentos", "cantidadTotal": 50, "cantidadBloqueada": 0, "codigo_ong": "ONG-550e8400-e29b-41d4-a716-446655440000" }
   ```
4. **Verificar** el stock inicial con `GET /api/recursos`: cada recurso debe mostrar `cantidadBloqueada: 0`.

Con estos datos, los ejemplos de la sección 3 funcionan tal cual: Cáritas puede comprometer hasta 100 unidades de Alimentos y 50 de Medicamentos. Para probar el rechazo por falta de disponibilidad basta pedir más que eso (por ejemplo, 500 unidades de Alimentos).

En Postman, la carpeta **2. ONGs y recursos (datos de prueba)** hace estos pasos automáticamente.

## 7. Probar con Postman

1. Importar `docs/postman/sistema-nacional.postman_collection.json` y uno de los entornos: `sistema-nacional.postman_environment.json` (local, `http://localhost:8080`) o `sistema-nacional-render.postman_environment.json` (Render).
2. Elegir el entorno en Postman (o ajustar la variable `baseUrl`).
3. Ejecutar la colección completa con el Collection Runner, de arriba hacia abajo. Cada request incluye sus verificaciones, el login guarda el token solo y la carpeta 2 prepara los datos.

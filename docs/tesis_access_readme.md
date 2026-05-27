**Base Access Tesis**

Este esquema modela el modo tesis como un mini-POS para microcomercios donde el numero Yape/Plin suele pertenecer al dueno, y los ayudantes reclaman pagos segun el puesto donde trabajan ese dia.

**Tablas**

- `app_users`: personas que inician sesion en la app.
- `businesses`: negocio creado por el dueno.
- `stalls`: puestos o zonas de venta del negocio.
- `business_members`: relacion entre usuario y negocio, con rol `OWNER` o `HELPER`.
- `invitations`: invitaciones creadas por el dueno para sumar ayudantes.
- `work_sessions`: jornada activa del ayudante; aqui se registra el puesto elegido al iniciar turno.
- `thesis_transactions`: pagos detectados y luego reclamados o confirmados.

**Idea funcional**

- El dueno crea el negocio y registra su numero Yape/Plin.
- El ayudante entra por invitacion.
- Al iniciar jornada, el ayudante elige el `stall` donde trabajara.
- Cuando llega un pago, la transaccion se detecta.
- Luego un ayudante la reclama y queda asociada a:
  - quien la reclamo
  - en que puesto estaba
  - en que jornada estaba
  - que descripcion manual le agrego

**Estados sugeridos**

- `business_status`: `ACTIVO`, `PAUSADO`
- `member_status`: `ACTIVO`, `INACTIVO`
- `invitation_status`: `PENDIENTE`, `ACTIVA`, `EXPIRADA`
- `session_status`: `ABIERTA`, `CERRADA`
- `transaction_status`: `DETECTADO`, `RECLAMADO`, `CONFIRMADO`, `OBSERVADO`

**Como usar el script en Access**

1. Crear una base nueva `.accdb`.
2. Ir a `Crear > Diseño de consulta`.
3. Cerrar la ventana de tablas.
4. Cambiar a `Vista SQL`.
5. Pegar y ejecutar cada sentencia del archivo [tesis_access_schema.sql](/C:/Users/Admin/AndroidStudioProjects/lectorYape/docs/tesis_access_schema.sql) una por una.

Nota:
Access a veces es sensible con varias sentencias juntas, por eso conviene ejecutar una por una, en orden.

**Como explicarlo al profesor**

- `app_users` guarda personas.
- `business_members` define el rol de cada persona dentro del negocio.
- `work_sessions` justifica que un ayudante pueda cambiar de puesto segun el dia.
- `thesis_transactions` demuestra la trazabilidad del pago:
  - detectado por el dueno
  - reclamado por un ayudante
  - asociado a un puesto
  - enriquecido con descripcion

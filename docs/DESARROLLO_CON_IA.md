# De una necesidad personal a una aplicación con ayuda de IA

## Punto de partida

La idea fue reunir en una sola aplicación información que suele quedar repartida entre las apps de distintos bancos: saldos, fechas y compras a meses sin intereses. La solución debía adaptarse a una forma concreta de organizar el dinero y poder modificarse al descubrir nuevas necesidades.

## El ciclo de trabajo

**Necesidad → requisito → propuesta de implementación con IA → revisión → prueba → corrección → nueva versión.**

El desarrollo se realizó por etapas. Las funciones de cuentas, movimientos y MSI dieron paso a presupuestos, detección de notificaciones, conciliación y respaldo. El uso cotidiano permitió encontrar comportamientos que un primer prototipo no cubría bien.

## Responsabilidades

| Actividad | Participación del autor | Apoyo de IA |
|---|---|---|
| Problema y alcance | Definir qué hacía falta y priorizar | Estructurar requisitos y explorar alternativas |
| Experiencia de uso | Elegir apariencia, navegación y ajustes | Proponer e implementar componentes y pantallas |
| Reglas financieras | Explicar el comportamiento esperado y contrastarlo | Traducir reglas a operaciones y validaciones |
| Programación | Dirigir cambios y revisar resultados | Generar y modificar código |
| Pruebas | Identificar casos, verificar resultados en el uso | Crear y ajustar pruebas y apoyar la depuración |
| Versionado | Definir entregables y comprobarlos | Apoyar cambios, migraciones y compilación automatizada |

## Retos que hicieron evolucionar la app

### Una transferencia, dos notificaciones

Una transferencia interna puede producir un aviso de salida y otro de entrada. La solución incorpora emparejamiento para representarla como un movimiento lógico, con tolerancia de importe y ventana temporal. La revisión manual sigue siendo necesaria.

### Separar deuda y mensualidad

La deuda total pendiente de una compra no es lo mismo que la mensualidad exigible. El modelo distingue el saldo del periodo, el posterior al corte y el pendiente MSI. Al corte, una mensualidad pasa de un componente a otro sin duplicar el total.

### Evitar descuentos duplicados

Un pago dirigido a un plan MSI debe reducir ese componente y la cuenta de origen una sola vez. Los casos de regresión ayudan a comprobar que no se reduzca también el saldo a pagar por error.

### Interpretar las alertas con cuidado

Una promoción, un código o una copia enviada por correo no debe convertirse en un gasto. El parser usa reglas y filtros; los movimientos van a una bandeja por confirmar.

### Mostrar diferencias sin inventar cifras

El agregado MSI de una tarjeta puede no coincidir con los planes capturados. La revisión muestra esa diferencia y permite corregir los datos disponibles sin repartirla arbitrariamente entre compras.

## Aprendizajes

Especificar el resultado esperado resultó tan necesario como generar el código. También fue necesario distinguir entre mejorar una pantalla y preservar las reglas del negocio: una interfaz puede verse bien mientras un pago altera dos veces el saldo.

Los cambios se sostuvieron con pruebas de regresión, versionado y migraciones. La IA permitió avanzar sobre distintas partes del proyecto, mientras la revisión y el uso aportaron los criterios para aceptar o corregir sus resultados.

## Publicación responsable del código

La edición pública tiene datos ficticios y un identificador de instalación independiente. Se retiraron datos iniciales particulares, correcciones de saldos específicos, excepciones personales en migraciones y restauración, referencias personales en pruebas y la firma privada. El repositorio público debe iniciarse con un historial nuevo.

Este documento describe el proceso general documentado. No es una transcripción de conversaciones, no contiene datos financieros reales y no atribuye tiempos o métricas de productividad que no se hayan medido.

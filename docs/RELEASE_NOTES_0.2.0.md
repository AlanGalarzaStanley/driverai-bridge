# DriverAI 0.2 — notas para el chofer

## Qué cambia para vos
- Después de **La acepté**, la app pregunta **¿Cobraste $X?** con el monto de la oferta.
- Un toque registra el ingreso en Billetera. Otro marca que no se concretó.
- En Registro, los viajes aceptados muestran **Cobré $X** con el monto precargado.

## APK actual (lectura)
Mientras el cobro en 1 toque termina de publicarse en CI:
- [APK debug run 78](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45951736904) — artefacto `driverai-debug-apk-78`.

## Probar primero (2 minutos)
1. Instalar el APK (si pide desinstalar la anterior, hacelo).
2. Activar accesibilidad + captura.
3. Evaluar una oferta real o de prueba.
4. Tocar **La acepté**.
5. Si aparece **¿Cobraste?** → confirmar. Si no, en Registro usar **Cobré**.

## Pendiente
- Keystore fijo (no desinstalar entre builds): ver SIGNING.md.
- Exportar/importar billetera.
- Aprendizaje «dónde me rinde» con tus datos reales.

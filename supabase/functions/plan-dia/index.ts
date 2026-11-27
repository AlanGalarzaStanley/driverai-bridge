// Edge Function plan-dia — schemaVersion 1
// Nunca devolver plan relleno.
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ ok: false, error: "method_not_allowed" }), { status: 405 });
  }
  return new Response(
    JSON.stringify({
      ok: false,
      error: "fuentes_no_configuradas",
      message: "Plan del día: falta configurar fuentes vigentes. La app no inventa eventos.",
    }),
    { headers: { "Content-Type": "application/json" }, status: 503 },
  );
});

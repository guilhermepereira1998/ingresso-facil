// Deno/Supabase adapter; the core handler is also usable as a Cloudflare Worker.
import { createHandler } from './macro-worker.mjs';
const handle = createHandler();
Deno.serve((request: Request) => handle(request, {
  GEMINI_API_KEY: Deno.env.get('GEMINI_API_KEY'),
  GEMINI_MODEL: Deno.env.get('GEMINI_MODEL'),
  COACH_ACCESS_TOKEN: Deno.env.get('COACH_ACCESS_TOKEN'),
}));

export const SYSTEM_PROMPT = `Você orienta um jogador de Wild Rift em português.
Use exclusivamente o estado observado. Não invente posição, inimigos ou objetivos.
Posições ausentes e detecções vazias significam dados desconhecidos.
Dê uma orientação curta de macrojogo, com no máximo 12 palavras.
Se não houver informação suficiente ou ação útil, use callout vazio.
Responda apenas JSON no formato {"callout":"..."}.`;

function json(value, status = 200) {
  return Response.json(value, { status, headers: { 'Cache-Control': 'no-store' } });
}
async function readLimited(body, limit = 65536) {
  if (!body) throw new Error('empty_body');
  const reader = body.getReader();
  const parts = []; let size = 0;
  try {
    while (true) {
      const item = await reader.read();
      if (item.done) break;
      size += item.value.byteLength;
      if (size > limit) { await reader.cancel(); throw new Error('body_too_large'); }
      parts.push(item.value);
    }
  } finally { reader.releaseLock(); }
  const merged = new Uint8Array(size); let offset = 0;
  for (const part of parts) { merged.set(part, offset); offset += part.length; }
  return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(merged));
}
export function validateState(value) {
  if (!value || !Number.isInteger(value.matchSeconds) || value.matchSeconds < 0 || value.matchSeconds > 7200) throw new Error('invalid_state');
  if (!Number.isInteger(value.currentGold) || value.currentGold < -1 || value.currentGold > 99999) throw new Error('invalid_state');
  const playerLane = typeof value.playerLane === 'string' ? value.playerLane.slice(0, 32) : '';
  const entities = ['ALIADO', 'INIMIGO', 'OBJETIVO_ATIVO', 'ONDA_MINIONS'];
  const detections = Array.isArray(value.minimapDetections) ? value.minimapDetections.slice(0, 100) : [];
  const minimapDetections = detections.filter(d => d && entities.includes(d.entityClass) &&
    [d.confidence, d.xNormalized, d.yNormalized].every(n => Number.isFinite(n) && n >= 0 && n <= 1))
    .map(({ entityClass, confidence, xNormalized, yNormalized }) => ({ entityClass, confidence, xNormalized, yNormalized }));
  const position = n => Number.isFinite(n) && n >= 0 && n <= 1 ? n : null;
  return { matchSeconds: value.matchSeconds, currentGold: value.currentGold, playerLane, minimapDetections,
    playerXNormalized: position(value.playerXNormalized), playerYNormalized: position(value.playerYNormalized) };
}
export function createHandler(upstream = fetch) {
  return async function handle(request, env) {
    const path = new URL(request.url).pathname;
    if (path === '/health' && request.method === 'GET') return json({ service: 'wildrift-macro', configured: Boolean(env.GEMINI_API_KEY && env.GEMINI_MODEL && env.COACH_ACCESS_TOKEN) });
    if (path !== '/' && path !== '/macro') return json({ error: 'not_found' }, 404);
    if (request.method !== 'POST') return json({ error: 'method_not_allowed' }, 405);
    if (!env.GEMINI_API_KEY || !env.GEMINI_MODEL || !env.COACH_ACCESS_TOKEN) return json({ error: 'service_not_configured' }, 503);
    if (request.headers.get('Authorization') !== `Bearer ${env.COACH_ACCESS_TOKEN}`) return json({ error: 'unauthorized' }, 401);
    if (!request.headers.get('Content-Type')?.toLowerCase().startsWith('application/json')) return json({ error: 'expected_json' }, 415);
    const advertised = Number(request.headers.get('Content-Length'));
    if (advertised > 65536) return json({ error: 'body_too_large' }, 413);
    let state;
    try { state = validateState((await readLimited(request.body)).state); }
    catch (error) { return json({ error: error.message === 'body_too_large' ? 'body_too_large' : 'invalid_state' }, error.message === 'body_too_large' ? 413 : 400); }
    if (!/^[a-zA-Z0-9._-]+$/.test(env.GEMINI_MODEL)) return json({ error: 'invalid_model_configuration' }, 503);
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 3000);
    try {
      const response = await upstream(`https://generativelanguage.googleapis.com/v1beta/models/${env.GEMINI_MODEL}:generateContent`, {
        method: 'POST', signal: controller.signal,
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': env.GEMINI_API_KEY },
        body: JSON.stringify({ systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
          contents: [{ role: 'user', parts: [{ text: JSON.stringify(state) }] }],
          generationConfig: { responseMimeType: 'application/json', responseSchema: { type: 'OBJECT', properties: { callout: { type: 'STRING' } }, required: ['callout'] }, maxOutputTokens: 128, temperature: .2 }
        })
      });
      if (!response.ok) return json({ error: 'provider_unavailable' }, response.status === 429 ? 429 : 502);
      const payload = await readLimited(response.body);
      const text = payload?.candidates?.[0]?.content?.parts?.filter(p => !p.thought && typeof p.text === 'string').map(p => p.text).join('') ?? '';
      let parsed;
      try { parsed = JSON.parse(text); } catch { return json({ error: 'invalid_provider_response' }, 502); }
      if (typeof parsed.callout !== 'string') return json({ error: 'invalid_provider_response' }, 502);
      const callout = parsed.callout.trim().replace(/\s+/g, ' ');
      if (callout.length > 240 || (callout && callout.split(' ').length > 12)) return json({ error: 'callout_too_long' }, 502);
      const meta = payload.usageMetadata;
      const safeCount = value => Number.isSafeInteger(value) && value >= 0 ? value : 0;
      const usage = meta ? {
        inputTokens: safeCount(meta.promptTokenCount),
        outputTokens: safeCount(meta.candidatesTokenCount) + safeCount(meta.thoughtsTokenCount),
        totalTokens: safeCount(meta.totalTokenCount)
      } : undefined;
      return json(usage ? { callout, usage } : { callout });
    } catch { return json({ error: controller.signal.aborted ? 'provider_timeout' : 'provider_unavailable' }, controller.signal.aborted ? 504 : 502); }
    finally { clearTimeout(timeout); }
  };
}
export default { fetch: createHandler() };

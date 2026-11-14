// Exemplo de Edge Function (Supabase / Cloudflare Workers) para Raciocínio Macro LLM
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

serve(async (req) => {
  const { systemPrompt, state } = await req.json();
  const apiKey = Deno.env.get("GEMINI_API_KEY");

  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: systemPrompt }] },
        contents: [{ parts: [{ text: JSON.stringify(state) }] }],
        generationConfig: {
          responseMimeType: "application/json",
          maxOutputTokens: 60,
          temperature: 0.3
        }
      })
    }
  );

  const data = await response.json();
  const rawText = data?.candidates?.[0]?.content?.parts?.[0]?.text ?? '{"callout":""}';
  return new Response(rawText, {
    headers: { "Content-Type": "application/json" }
  });
});

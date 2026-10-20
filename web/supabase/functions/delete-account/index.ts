// Supabase Edge Function: delete-account
// Deploy:  supabase functions deploy delete-account --no-verify-jwt
// Secrets: supabase secrets set FIREBASE_API_KEY=<web api key>   (SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY exist by default)
//
// Auth is Firebase, not Supabase, so the caller sends its Firebase ID token in the body. We ask Google
// who the token belongs to (accounts:lookup), then delete that person's rows with the service role key.
// Safe to call twice. The clients delete the Firebase user themselves right after this succeeds.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

async function sha256Hex(text: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "method" }, 405);

  let idToken = "";
  try { idToken = String((await req.json()).idToken || ""); } catch { /* fall through */ }
  if (!idToken) return json({ error: "idToken required" }, 400);

  const apiKey = Deno.env.get("FIREBASE_API_KEY");
  if (!apiKey) return json({ error: "server not configured" }, 500);

  // Who is this? (Google verifies signature and expiry for us.)
  const lookup = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${apiKey}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ idToken }),
  });
  if (!lookup.ok) return json({ error: "invalid token" }, 401);
  const user = (await lookup.json()).users?.[0];
  const uid: string | undefined = user?.localId;
  const email: string = String(user?.email || "").trim();
  if (!uid) return json({ error: "invalid token" }, 401);

  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const fail = (step: string, error: { message: string } | null) => {
    console.error("delete-account", step, error);
    return json({ error: step }, 500);
  };

  // 1) Rows keyed by Firebase uid.
  for (const table of ["user_data", "practice_progress"]) {
    const { error } = await db.from(table).delete().eq("firebase_uid", uid);
    if (error) return fail(table, error);
  }

  if (email) {
    const emails = [...new Set([email, email.toLowerCase()])];

    // 2) Rows keyed by e-mail that are personal data.
    for (const table of ["enrollment_requests", "members"]) {
      const { error } = await db.from(table).delete().in("email", emails);
      if (error) return fail(table, error);
    }

    // 3) Live exam scores stay in the merit list but lose name and e-mail. The replacement e-mail is
    //    the same for every exam of this user, and (exam_id, member_email) stays unique.
    const alias = `deleted-${(await sha256Hex(uid)).slice(0, 16)}@deleted.invalid`;
    const { error } = await db
      .from("live_exam_submissions")
      .update({ member_name: "Deleted user", member_email: alias })
      .in("member_email", emails);
    if (error) return fail("live_exam_submissions", error);
  }

  return json({ ok: true });
});

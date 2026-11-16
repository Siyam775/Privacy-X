// PrivacyX Supabase Edge Function: 7-Day Expired Cloud Media Cleaner
// Deletes expired files from `chat-media` bucket first, confirms deletion, then marks metadata deleted.
// Deploy without GitHub: supabase functions deploy cleanup-expired-media --no-verify-jwt

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const BUCKET = "chat-media";

Deno.serve(async (req) => {
  const cronSecret = Deno.env.get("CLEANUP_CRON_SECRET") || "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";

  if (!supabaseUrl || !serviceRoleKey) {
    return Response.json({ error: "Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY" }, { status: 500 });
  }

  const providedCronSecret = req.headers.get("x-cron-secret") || "";
  const authHeader = req.headers.get("authorization") || "";
  const bearerToken = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";

  const authorizedByCron = Boolean(cronSecret && providedCronSecret === cronSecret);
  const authorizedByServiceRole = bearerToken === serviceRoleKey;

  if (!authorizedByCron && !authorizedByServiceRole) {
    return Response.json({ error: "Unauthorized: valid CLEANUP_CRON_SECRET or service_role Bearer required" }, { status: 401 });
  }

  const supabase = createClient(supabaseUrl, serviceRoleKey);

  const nowIso = new Date().toISOString();
  const { data: expiredRows, error } = await supabase
    .from("media_objects")
    .select("media_id, object_path, thumb_path")
    .eq("deleted_from_storage", false)
    .lte("expires_at", nowIso)
    .limit(200);

  if (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }

  let deletedCount = 0;
  for (const row of expiredRows || []) {
    const pathsToDelete = [row.object_path, row.thumb_path].filter(
      (p): p is string => typeof p === "string" && p.length > 0
    );
    const { error: removeErr } = await supabase.storage.from(BUCKET).remove(pathsToDelete);
    if (!removeErr) {
      await supabase
        .from("media_objects")
        .update({ deleted_from_storage: true, deleted_at: new Date().toISOString() })
        .eq("media_id", row.media_id);
      deletedCount += 1;
    }
  }

  return Response.json({
    checked: (expiredRows || []).length,
    deletedCount,
    executedAt: nowIso,
  });
});

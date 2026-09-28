import { createClient } from "npm:@supabase/supabase-js@2";
import { GoogleAuth } from "npm:google-auth-library@9.15.0";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Content-Type": "application/json",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: cors });
  }

  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    if (!authHeader.startsWith("Bearer ")) {
      return new Response(JSON.stringify({ error: "Admin authorization required." }), {
        status: 401,
        headers: cors,
      });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const firebaseJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON")!;

    const supabase = createClient(supabaseUrl, serviceKey);

    const userToken = authHeader.substring("Bearer ".length);
    const { data: userData, error: userError } =
      await supabase.auth.getUser(userToken);

    if (userError || !userData.user) {
      return new Response(JSON.stringify({ error: "Invalid admin session." }), {
        status: 401,
        headers: cors,
      });
    }

    const { data: profile, error: profileError } = await supabase
      .from("profiles")
      .select("role,active")
      .eq("id", userData.user.id)
      .maybeSingle();

    if (profileError || profile?.role !== "ADMIN" || profile?.active !== true) {
      return new Response(JSON.stringify({ error: "ADMIN access required." }), {
        status: 403,
        headers: cors,
      });
    }

    const body = await req.json();
    const updateId = String(body?.update_id ?? "").trim();
    if (!updateId) {
      return new Response(JSON.stringify({ error: "update_id is required." }), {
        status: 400,
        headers: cors,
      });
    }

    const { data: update, error: updateError } = await supabase
      .from("admin_price_updates")
      .select("id,item_count")
      .eq("id", updateId)
      .eq("created_by", userData.user.id)
      .maybeSingle();

    if (updateError || !update) {
      return new Response(JSON.stringify({ error: "Admin Push update not found." }), {
        status: 404,
        headers: cors,
      });
    }

    const { data: targets, error: targetError } = await supabase
      .from("admin_price_update_targets")
      .select("store_id")
      .eq("update_id", updateId);

    if (targetError) throw targetError;

    const storeIds = [...new Set((targets ?? []).map((row) => row.store_id))];
    if (storeIds.length === 0) {
      return new Response(JSON.stringify({ sent: 0 }), { headers: cors });
    }

    const { data: devices, error: deviceError } = await supabase
      .from("store_devices")
      .select("device_id,store_id,fcm_token,active")
      .in("store_id", storeIds)
      .eq("active", true)
      .not("fcm_token", "is", null);

    if (deviceError) throw deviceError;

    const tokens = [...new Map(
      (devices ?? [])
        .filter((d) => typeof d.fcm_token === "string" && d.fcm_token.trim())
        .map((d) => [d.fcm_token, d])
    ).values()];

    if (tokens.length === 0) {
      return new Response(JSON.stringify({
        sent: 0,
        registered_targets: storeIds.length,
        reason: "No FCM tokens registered yet.",
      }), { headers: cors });
    }

    const serviceAccount = JSON.parse(firebaseJson);
    const auth = new GoogleAuth({
      credentials: serviceAccount,
      scopes: ["https://www.googleapis.com/auth/firebase.messaging"],
    });
    const accessToken = await auth.getAccessToken();
    if (!accessToken) throw new Error("Could not obtain Firebase access token.");

    const endpoint =
      "https://fcm.googleapis.com/v1/projects/" +
      encodeURIComponent(serviceAccount.project_id) +
      "/messages:send";

    let sent = 0;
    let failed = 0;

    for (const device of tokens) {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: {
          "Authorization": "Bearer " + accessToken,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: device.fcm_token,
            // Send a high-priority data message so our FirebaseMessagingService
            // handles both foreground and background delivery consistently.
            // The Android service creates the notification locally.
            data: {
              type: "ADMIN_PRICE_UPDATE",
              update_id: updateId,
              price_count: String(update.item_count),
            },
            android: {
              priority: "HIGH",
              notification: {
                channel_id: "admin_price_updates",
              },
            },
          },
        }),
      });

      if (response.ok) {
        sent++;
      } else {
        failed++;
      }
    }

    return new Response(JSON.stringify({
      sent,
      failed,
      registered_targets: storeIds.length,
      devices: tokens.length,
    }), { headers: cors });
  } catch (error) {
    return new Response(JSON.stringify({
      error: error instanceof Error ? error.message : String(error),
    }), {
      status: 500,
      headers: cors,
    });
  }
});

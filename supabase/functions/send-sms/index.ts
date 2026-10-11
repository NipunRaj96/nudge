// Supabase Auth "Send SMS" hook: Auth generates + verifies the OTP, this only delivers it.
// Delivery = one HTTP GET to an SMS gateway. Placeholders: {phone} 10-digit mobile, {otp} the code, {msg} full URL-encoded text.
// Fast2SMS OTP route (no DLT setup needed, recommended to start):
//   https://www.fast2sms.com/dev/bulkV2?authorization=<API_KEY>&route=otp&variables_values={otp}&numbers={phone}
// Any DLT gateway (Jio etc.): https://gateway.example/send?key=K&to={phone}&msg={msg}&templateid=<DLT_ID>
// Empty template = dev mode (OTP is only logged).
import { Webhook } from "https://esm.sh/standardwebhooks@1.0.0";

const secret = Deno.env.get("SEND_SMS_HOOK_SECRET")?.replace("v1,whsec_", "") ?? "";
const template = Deno.env.get("SMS_URL_TEMPLATE") ?? "";

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  let user: { phone: string }, sms: { otp: string };
  try {
    ({ user, sms } = new Webhook(secret).verify(await req.text(), Object.fromEntries(req.headers)) as any);
  } catch {
    return json({ error: { http_code: 401, message: "bad signature" } }, 401);
  }

  // The DLT-registered text must match this exactly once the template is approved.
  const msg = `${sms.otp} is your Nudge login code. Do not share it with anyone.`;
  const phone = user.phone.replace(/^\+?91/, "");
  if (!template) {
    console.log(`[dev] OTP for ${user.phone}: ${sms.otp}`);
    return json({});
  }
  const url = template.replace("{phone}", phone).replace("{otp}", sms.otp).replace("{msg}", encodeURIComponent(msg));
  const res = await fetch(url).catch((e) => new Response(String(e), { status: 599 }));
  if (!res.ok) {
    console.error("SMS gateway failed", res.status, await res.text());
    return json({ error: { http_code: 502, message: "Could not send SMS. Try again." } }, 502);
  }
  return json({});
});

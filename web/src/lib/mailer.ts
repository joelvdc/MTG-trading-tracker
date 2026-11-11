// Sending email through Resend (https://resend.com; free plan: 3,000 a month). Needs two Vercel settings:
// RESEND_API_KEY (the key, a secret) and EMAIL_FROM ("MTG Trader <mtgtrader@mail.example.com>", an address on a domain
// verified at Resend). Without them the website sends nothing and says so.

export interface Mail {
  to: string;
  subject: string;
  text: string;
  html: string;
  /** For the "List-Unsubscribe" header (the stop link), so mail programs offer it too. */
  unsubscribe?: string;
}

export type Send = (m: Mail) => Promise<void>;

export class MailError extends Error {}

/** Both settings are there. */
export function mailReady(): boolean {
  return !!(process.env.RESEND_API_KEY ?? "").trim() && !!(process.env.EMAIL_FROM ?? "").trim();
}

export const NOT_READY = "Email isn't set up on the website yet (the owner adds the Resend key in Vercel).";

/** Sends one email; throws a MailError with Resend's message when it's refused. */
export const sendMail: Send = async (m) => {
  const key = (process.env.RESEND_API_KEY ?? "").trim();
  const from = (process.env.EMAIL_FROM ?? "").trim();
  if (!key || !from) throw new MailError(NOT_READY);
  const r = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      from,
      to: [m.to],
      subject: m.subject,
      text: m.text,
      html: m.html,
      ...(m.unsubscribe ? { headers: { "List-Unsubscribe": `<${m.unsubscribe}>`, "List-Unsubscribe-Post": "List-Unsubscribe=One-Click" } } : {}),
    }),
  });
  if (!r.ok) {
    const body = (await r.json().catch(() => ({}))) as { message?: string };
    throw new MailError(`The email couldn't be sent: ${body.message ?? `Resend answered ${r.status}`}.`);
  }
};

// The small page the email links open (confirm, stop): plain HTML, no login needed. [button] adds a form that posts
// to [action] (links in emails only ask; the button does it).
export function emailPage(title: string, text: string, status = 200, button?: { action: string; label: string }): Response {
  const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  const form = button
    ? `<form method="post" action="${esc(button.action)}"><button type="submit" style="background:#7e57c2;color:#fff;border:0;border-radius:6px;padding:10px 18px;font-size:15px;cursor:pointer">${esc(button.label)}</button></form>`
    : "";
  const html = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex"><title>${esc(title)} · MTG Trader</title></head>
<body style="margin:0;background:#15131a;color:#ece8f4;font-family:Arial,Helvetica,sans-serif">
<main style="max-width:520px;margin:12vh auto;padding:0 16px"><h1 style="font-size:22px">MTG <span style="color:#e0a526">Trader</span></h1>
<h2 style="font-size:18px">${esc(title)}</h2><p style="line-height:1.5">${esc(text)}</p>${form}
<p><a href="/settings" style="color:#b39ddb">Settings on the website</a></p></main></body></html>`;
  return new Response(html, { status, headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", "X-Robots-Tag": "noindex" } });
}

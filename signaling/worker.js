/** Cloudflare Worker signaling for BQ Sqwad Share. Relays SDP/ICE only — never file bytes. */
const rooms = new Map();

export default {
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === "/health") return new Response("ok");
    if (url.pathname !== "/ws") return new Response("BQ Sqwad Share signaling", { status: 200 });
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("Expected WebSocket", { status: 426 });
    }
    const role = url.searchParams.get("role") || "receiver";
    const code = (url.searchParams.get("code") || "").toUpperCase();
    if (!code || code.length < 4) return new Response("Bad code", { status: 400 });
    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    server.accept();
    if (!rooms.has(code)) rooms.set(code, { sender: null, receivers: new Set(), created: Date.now() });
    const room = rooms.get(code);
    if (Date.now() - room.created > 15 * 60 * 1000) {
      rooms.delete(code);
      server.send(JSON.stringify({ type: "error", message: "Code expired" }));
      server.close();
      return new Response(null, { status: 101, webSocket: client });
    }
    if (role === "sender") {
      room.sender = server;
      server.send(JSON.stringify({ type: "waiting" }));
    } else {
      room.receivers.add(server);
      server.send(JSON.stringify({ type: "waiting" }));
      if (room.sender) room.sender.send(JSON.stringify({ type: "receiver_joined" }));
    }
    server.addEventListener("message", (event) => {
      let msg;
      try { msg = JSON.parse(event.data); } catch { return; }
      const targets = [];
      if (role === "sender") targets.push(...room.receivers);
      else if (room.sender) targets.push(room.sender);
      for (const t of targets) {
        try { t.send(JSON.stringify(msg)); } catch {}
      }
    });
    server.addEventListener("close", () => {
      if (room.sender === server) room.sender = null;
      room.receivers.delete(server);
    });
    return new Response(null, { status: 101, webSocket: client });
  },
};

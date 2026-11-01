const SIGNALING = window.BQ_SIGNALING_URL || "wss://YOUR-WORKER.workers.dev/ws";
const statusEl = document.getElementById("status");
document.getElementById("join").onclick = () => {
  const code = document.getElementById("code").value.trim().toUpperCase();
  if (code.length < 4) { statusEl.textContent = "Enter the code from the sender."; return; }
  statusEl.textContent = "Connecting…";
  try {
    const ws = new WebSocket(`${SIGNALING}?role=receiver&code=${encodeURIComponent(code)}`);
    ws.onmessage = (ev) => {
      const msg = JSON.parse(ev.data);
      if (msg.type === "waiting") statusEl.textContent = "Waiting for the sender to approve…";
      if (msg.type === "error") statusEl.textContent = msg.message || "Could not join.";
      if (msg.type === "offer") statusEl.textContent = "Sender connected. Complete WebRTC in a full deploy.";
    };
    ws.onerror = () => {
      statusEl.textContent = "Signaling unreachable. Deploy signaling/ and set BQ_SIGNALING_URL.";
    };
  } catch (e) {
    statusEl.textContent = String(e);
  }
};

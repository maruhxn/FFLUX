// FFLUX 미디어 스파이크 공용 스크립트 (이슈 #2). 빌드 없이 각 HTML에서 <script src="spike.js">로 불러 쓴다.
// 모든 URL은 same-origin 상대 경로: WHIP/WHEP는 /media/<path>/whip|whep, HLS는 /media/<path>/index.m3u8

const $ = (id) => document.getElementById(id);

function log(msg) {
  const el = $("log");
  const line = `[${new Date().toLocaleTimeString()}] ${msg}`;
  console.log(line);
  if (el) el.textContent = line + "\n" + el.textContent;
}

function mediaUrl(path, suffix) {
  return `/media/${path.replace(/^\/+|\/+$/g, "")}/${suffix}`;
}

// 송출 트랜시버의 코덱 순서를 바꿔 원하는 코덱만 협상되게 한다(영상 H264, 오디오 Opus).
function preferCodec(transceiver, kind, mimeType) {
  if (typeof transceiver.setCodecPreferences !== "function" || !RTCRtpSender.getCapabilities) {
    log(`경고: 이 브라우저는 setCodecPreferences를 지원하지 않습니다(${kind})`);
    return false;
  }
  const codecs = RTCRtpSender.getCapabilities(kind).codecs;
  const wanted = codecs.filter((c) => c.mimeType.toLowerCase() === mimeType.toLowerCase());
  if (wanted.length === 0) {
    log(`경고: 이 브라우저에 ${mimeType} 코덱이 없습니다`);
    return false;
  }
  // packetization-mode=1 + baseline(42e01f)을 앞에 둬 호환성이 가장 넓은 H264를 먼저 고르게 한다.
  wanted.sort((a, b) => score(b) - score(a));
  transceiver.setCodecPreferences(wanted);
  return true;

  function score(c) {
    const f = c.sdpFmtpLine || "";
    return (f.includes("packetization-mode=1") ? 2 : 0) + (f.includes("profile-level-id=42e01f") ? 1 : 0);
  }
}

function waitIceGathering(pc, timeoutMs = 3000) {
  if (pc.iceGatheringState === "complete") return Promise.resolve();
  return new Promise((resolve) => {
    const done = () => {
      pc.removeEventListener("icegatheringstatechange", check);
      resolve();
    };
    const check = () => pc.iceGatheringState === "complete" && done();
    pc.addEventListener("icegatheringstatechange", check);
    setTimeout(done, timeoutMs);
  });
}

async function postSdp(url, sdp) {
  const res = await fetch(url, { method: "POST", headers: { "Content-Type": "application/sdp" }, body: sdp });
  if (res.status !== 201) {
    throw new Error(`${url} → HTTP ${res.status} ${await res.text()}`);
  }
  const location = res.headers.get("Location");
  // 세션 리소스 URL(종료 시 DELETE). Caddy가 절대 경로에 /media 접두사를 붙여 준다.
  const resourceUrl = location ? new URL(location, new URL(url, window.location.href)).href : null;
  return { answer: await res.text(), resourceUrl };
}

// WHIP 송출. stream의 영상은 H264, 오디오는 Opus로 강제한다.
async function whipPublish(path, stream) {
  const pc = new RTCPeerConnection();
  for (const track of stream.getTracks()) {
    const tr = pc.addTransceiver(track, { direction: "sendonly", streams: [stream] });
    if (track.kind === "video") preferCodec(tr, "video", "video/H264");
    if (track.kind === "audio") preferCodec(tr, "audio", "audio/opus");
  }
  pc.addEventListener("connectionstatechange", () => log(`송출 연결 상태: ${pc.connectionState}`));
  await pc.setLocalDescription(await pc.createOffer());
  await waitIceGathering(pc);
  const url = mediaUrl(path, "whip");
  const { answer, resourceUrl } = await postSdp(url, pc.localDescription.sdp);
  await pc.setRemoteDescription({ type: "answer", sdp: answer });
  log(`WHIP 시그널링 완료: ${url}`);
  return { pc, resourceUrl };
}

// WHIP/WHEP 세션 종료: 리소스 DELETE 후 PeerConnection 정리.
async function closeSession(session) {
  if (!session) return;
  if (session.resourceUrl) {
    try {
      await fetch(session.resourceUrl, { method: "DELETE" });
    } catch (e) {
      log(`세션 DELETE 실패(무시): ${e.message}`);
    }
  }
  session.pc.close();
}

// WHEP 재생. timeoutMs 안에 connected가 되지 않으면 실패로 본다.
async function whepPlay(path, video, timeoutMs = 8000) {
  const pc = new RTCPeerConnection();
  pc.addTransceiver("video", { direction: "recvonly" });
  pc.addTransceiver("audio", { direction: "recvonly" });
  const stream = new MediaStream();
  pc.addEventListener("track", (e) => {
    stream.addTrack(e.track);
    video.srcObject = stream;
  });
  pc.addEventListener("connectionstatechange", () => log(`시청 연결 상태: ${pc.connectionState}`));
  await pc.setLocalDescription(await pc.createOffer());
  await waitIceGathering(pc);
  const url = mediaUrl(path, "whep");
  let session;
  try {
    const { answer, resourceUrl } = await postSdp(url, pc.localDescription.sdp);
    session = { pc, resourceUrl };
    await pc.setRemoteDescription({ type: "answer", sdp: answer });
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`WebRTC 연결 시간 초과(${timeoutMs}ms)`)), timeoutMs);
      const check = () => {
        if (pc.connectionState === "connected") {
          clearTimeout(timer);
          resolve();
        } else if (pc.connectionState === "failed") {
          clearTimeout(timer);
          reject(new Error("WebRTC 연결 실패"));
        }
      };
      pc.addEventListener("connectionstatechange", check);
      check();
    });
  } catch (e) {
    await closeSession(session || { pc });
    video.srcObject = null;
    throw e;
  }
  return session;
}

// HLS 재생. Safari(iOS 포함)는 네이티브 HLS, 그 외는 hls.js.
let hlsJsLoading;
function loadHlsJs() {
  hlsJsLoading ??= new Promise((resolve, reject) => {
    const s = document.createElement("script");
    s.src = "https://cdn.jsdelivr.net/npm/hls.js@1/dist/hls.min.js";
    s.onload = () => resolve(window.Hls);
    s.onerror = () => reject(new Error("hls.js 로드 실패"));
    document.head.appendChild(s);
  });
  return hlsJsLoading;
}

async function hlsPlay(path, video) {
  const url = mediaUrl(path, "index.m3u8");
  // 최근 Chrome도 canPlayType이 참이지만 Opus 포함 HLS를 재생하지 못해 Safari일 때만 네이티브를 쓴다.
  const isSafari = /^((?!chrome|android|crios|fxios|edg).)*safari/i.test(navigator.userAgent);
  if (isSafari && video.canPlayType("application/vnd.apple.mpegurl")) {
    video.src = url;
    log(`HLS 네이티브 재생: ${url}`);
    return { native: true, destroy: () => video.removeAttribute("src") };
  }
  const Hls = await loadHlsJs();
  if (!Hls.isSupported()) throw new Error("이 브라우저는 HLS를 재생할 수 없습니다");
  const hls = new Hls();
  hls.on(Hls.Events.ERROR, (_, d) => d.fatal && log(`hls.js 오류: ${d.type} ${d.details}`));
  hls.loadSource(url);
  hls.attachMedia(video);
  log(`HLS(hls.js) 재생: ${url}`);
  return { native: false, destroy: () => hls.destroy() };
}

// getStats로 실제 협상된 코덱을 읽는다. type: "outbound-rtp"(송출) 또는 "inbound-rtp"(시청)
async function describeCodecs(pc, type) {
  const stats = await pc.getStats();
  const parts = [];
  stats.forEach((r) => {
    if (r.type !== type || !r.codecId) return;
    const codec = stats.get(r.codecId);
    if (!codec) return;
    let text = `${r.kind}: ${codec.mimeType}`;
    if (codec.sdpFmtpLine) text += ` (${codec.sdpFmtpLine})`;
    if (r.kind === "video" && r.frameWidth) text += ` ${r.frameWidth}x${r.frameHeight}@${Math.round(r.framesPerSecond || 0)}fps`;
    if (r.encoderImplementation) text += ` [encoder: ${r.encoderImplementation}]`;
    if (r.decoderImplementation) text += ` [decoder: ${r.decoderImplementation}]`;
    parts.push(text);
  });
  return parts.length ? parts.join("\n") : "(아직 통계 없음)";
}

// session이 살아 있는 동안 1초마다 코덱 정보를 el에 표시한다. 반환값을 호출하면 멈춘다.
function watchCodecs(pc, type, el) {
  const timer = setInterval(async () => {
    if (pc.connectionState === "closed") return clearInterval(timer);
    el.textContent = await describeCodecs(pc, type);
  }, 1000);
  return () => clearInterval(timer);
}

function viewerLink(path) {
  return `${location.origin}/watch.html?path=${encodeURIComponent(path)}`;
}

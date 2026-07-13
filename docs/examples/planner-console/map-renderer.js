const ROUTE_COLORS = {
  WALK: "#5f6b70",
  BUS: "#087f5b",
  SUBWAY: "#e8590c",
  TRANSIT: "#087f5b",
};

let kakaoPromise;

function loadKakaoMaps() {
  if (window.kakao?.maps) return Promise.resolve(window.kakao.maps);
  if (kakaoPromise) return kakaoPromise;
  const appkey = window.KAKAO_JAVASCRIPT_KEY;
  if (!appkey) return Promise.reject(new Error("Kakao JavaScript 키가 없습니다."));

  kakaoPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appkey)}&autoload=false`;
    script.onload = () => window.kakao.maps.load(() => resolve(window.kakao.maps));
    script.onerror = () => reject(new Error("Kakao Maps SDK를 불러오지 못했습니다."));
    document.head.append(script);
  });
  return kakaoPromise;
}

function allCoordinates(mapData) {
  const points = [];
  const add = (point) => {
    if (point?.longitude != null && point?.latitude != null) points.push([Number(point.longitude), Number(point.latitude)]);
  };
  add(mapData.startMarker);
  (mapData.markers || []).forEach(add);
  add(mapData.endMarker);
  (mapData.routeLines || []).forEach((line) => (line.coordinates || []).forEach((point) => points.push(point.map(Number))));
  return points.filter(([lng, lat]) => Number.isFinite(lng) && Number.isFinite(lat));
}

export async function renderMap(container, mapData) {
  try {
    const maps = await loadKakaoMaps();
    renderKakaoMap(maps, container, mapData);
    return "Kakao Maps";
  } catch (error) {
    renderFallbackMap(container, mapData);
    return `좌표 미리보기 · ${error.message}`;
  }
}

function renderKakaoMap(maps, container, mapData) {
  container.replaceChildren();
  const map = new maps.Map(container, {
    center: new maps.LatLng(35.1796, 129.0756),
    level: 7,
  });
  const bounds = new maps.LatLngBounds();

  const markerItems = [
    { ...mapData.startMarker, label: "S" },
    ...(mapData.markers || []).map((marker) => ({ ...marker, label: String(marker.order) })),
    { ...mapData.endMarker, label: "E" },
  ].filter((item) => item.longitude != null && item.latitude != null);

  markerItems.forEach((item) => {
    const position = new maps.LatLng(Number(item.latitude), Number(item.longitude));
    bounds.extend(position);
    const content = document.createElement("div");
    content.textContent = item.label;
    Object.assign(content.style, {
      width: "28px", height: "28px", display: "grid", placeItems: "center",
      color: "white", background: item.label === "S" || item.label === "E" ? "#172127" : "#e8590c",
      border: "3px solid white", borderRadius: "50%", boxShadow: "0 2px 7px rgba(0,0,0,.28)",
      font: "700 11px system-ui",
    });
    new maps.CustomOverlay({ position, content, yAnchor: 0.5 }).setMap(map);
  });

  (mapData.routeLines || []).forEach((line) => {
    const path = (line.coordinates || []).map(([lng, lat]) => {
      const point = new maps.LatLng(Number(lat), Number(lng));
      bounds.extend(point);
      return point;
    });
    if (path.length < 2) return;
    new maps.Polyline({
      map,
      path,
      strokeWeight: line.mode === "WALK" ? 4 : 6,
      strokeColor: ROUTE_COLORS[line.mode] || ROUTE_COLORS.TRANSIT,
      strokeOpacity: 0.82,
      strokeStyle: line.mode === "WALK" ? "shortdash" : "solid",
    });
  });

  if (!bounds.isEmpty()) map.setBounds(bounds, 48, 48, 48, 48);
  window.setTimeout(() => map.relayout(), 0);
}

function renderFallbackMap(container, mapData) {
  const points = allCoordinates(mapData);
  if (!points.length) {
    container.innerHTML = '<div class="result-empty"><strong>표시할 좌표가 없습니다.</strong></div>';
    return;
  }
  const lngs = points.map(([lng]) => lng);
  const lats = points.map(([, lat]) => lat);
  const minLng = Math.min(...lngs), maxLng = Math.max(...lngs);
  const minLat = Math.min(...lats), maxLat = Math.max(...lats);
  const project = ([lng, lat]) => {
    const x = 50 + ((lng - minLng) / (maxLng - minLng || 1)) * 900;
    const y = 550 - ((lat - minLat) / (maxLat - minLat || 1)) * 500;
    return [x, y];
  };
  const lines = (mapData.routeLines || []).map((line) => {
    const path = (line.coordinates || []).map(project).map((point) => point.join(",")).join(" ");
    return `<polyline points="${path}" fill="none" stroke="${ROUTE_COLORS[line.mode] || ROUTE_COLORS.TRANSIT}" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>`;
  }).join("");
  const markerPoints = [mapData.startMarker, ...(mapData.markers || []), mapData.endMarker].filter(Boolean);
  const markers = markerPoints.map((marker, index) => {
    const [x, y] = project([Number(marker.longitude), Number(marker.latitude)]);
    const label = index === 0 ? "S" : index === markerPoints.length - 1 ? "E" : String(marker.order || index);
    return `<g><circle cx="${x}" cy="${y}" r="15" fill="${label === "S" || label === "E" ? "#172127" : "#e8590c"}" stroke="white" stroke-width="5"/><text x="${x}" y="${y + 4}" text-anchor="middle" fill="white" font-size="11" font-weight="700">${label}</text></g>`;
  }).join("");
  container.innerHTML = `<svg class="fallback-map" viewBox="0 0 1000 600" role="img" aria-label="일정 좌표 경로"><rect width="1000" height="600" fill="#dfe8e3"/>${lines}${markers}</svg>`;
}

const escapeHtml = (value) => String(value ?? "")
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;")
  .replaceAll("'", "&#039;");

const formatTime = (value) => value ? String(value).slice(0, 5) : "--:--";

const formatDate = (value) => {
  if (!value) return "";
  return new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric", weekday: "short" })
    .format(new Date(`${value}T00:00:00`));
};

const totalTransitMinutes = (day) => [...(day.stops || []).map((stop) => stop.inboundTransit), day.finalTransit]
  .filter(Boolean)
  .reduce((sum, transit) => sum + (transit.totalMinutes || 0), 0);

export function renderSummary(schedule) {
  const days = schedule.days || [];
  const stopCount = days.reduce((sum, day) => sum + (day.stops?.length || 0), 0);
  const transitMinutes = days.reduce((sum, day) => sum + totalTransitMinutes(day), 0);
  return `
    <div class="metric"><span>상태</span><strong>${escapeHtml(schedule.status)}</strong></div>
    <div class="metric"><span>기간</span><strong>${days.length}일 · ${stopCount}곳</strong></div>
    <div class="metric"><span>총 이동</span><strong>${transitMinutes}분</strong></div>
    <div class="metric"><span>스타일</span><strong title="${escapeHtml(schedule.styleSummary)}">${escapeHtml(schedule.styleSummary)}</strong></div>
  `;
}

export function renderEvaluation(evaluation) {
  if (!evaluation) return "";
  const quality = evaluation.qualityScore;
  const operations = evaluation.operations;
  const hardGate = evaluation.hardGate;
  const cacheRate = operations.routeResolutionCount
    ? Math.round((operations.routeCacheHitCount / operations.routeResolutionCount) * 100)
    : 0;
  const metrics = (quality.metrics || []).map((metric) => {
    const rate = metric.maxScore ? Math.round((metric.score / metric.maxScore) * 100) : 0;
    return `
      <div class="score-metric">
        <div class="score-label"><strong>${escapeHtml(metric.label)}</strong><span>${metric.score}/${metric.maxScore}</span></div>
        <div class="score-track"><i style="width:${Math.max(0, Math.min(100, rate))}%"></i></div>
        <p>${escapeHtml(metric.reason)}</p>
      </div>
    `;
  }).join("");
  return `
    <div class="evaluation-heading">
      <div class="score-total"><span>Planner 품질</span><strong>${quality.totalScore}<small>/${quality.maxScore}</small></strong></div>
      <span class="gate-status ${hardGate.passed ? "is-pass" : "is-fail"}">Hard Gate ${hardGate.passed ? "PASS" : "FAIL"}</span>
      <div class="operation-summary">
        <span>생성 <strong>${(operations.generationMillis / 1000).toFixed(2)}초</strong></span>
        <span>외부 HTTP <strong>${operations.externalHttpCallCount}회</strong></span>
        <span>캐시 <strong>${operations.routeCacheHitCount}회 · ${cacheRate}%</strong></span>
        <span>Geometry fallback <strong>${operations.geometryFallbackLineCount}건</strong></span>
      </div>
    </div>
    <div class="score-metrics">${metrics}</div>
    <div class="evaluation-foot">
      <span>${escapeHtml((operations.providers || []).join(" + ") || "Provider 없음")}</span>
      <span>경로검색 ${operations.odsayPathSearchCount}회</span>
      <span>loadLane ${operations.odsayLoadLaneCount}회</span>
      <span>TMAP ${operations.tmapWalkingCount}회</span>
      <span>경로 ${operations.routeCount}개</span>
      <span>이동 ${operations.totalTransitMinutes}분</span>
      <span>도보 ${operations.totalWalkMinutes}분</span>
      <span>환승 ${operations.totalTransferCount}회</span>
      ${operations.externalHttpFailureCount ? `<span class="operation-warning">외부 HTTP 실패 ${operations.externalHttpFailureCount}회</span>` : ""}
    </div>
  `;
}

export function renderDayTabs(days, activeDayNo) {
  return days.map((day) => `
    <button class="day-tab ${day.dayNo === activeDayNo ? "is-active" : ""}" type="button"
      data-day-no="${day.dayNo}" role="tab" aria-selected="${day.dayNo === activeDayNo}">
      ${day.dayNo}일차 <small>${escapeHtml(formatDate(day.date))}</small>
    </button>
  `).join("");
}

function renderTags(stop) {
  const reasons = (stop.selectionReasons || []).map((reason) => `<span class="tag">${escapeHtml(reason)}</span>`);
  const warnings = (stop.warnings || []).map((warning) => `<span class="tag warning">${escapeHtml(warning)}</span>`);
  return [...reasons, ...warnings].join("");
}

function transitDetails(transit) {
  if (!transit) return "";
  const segments = (transit.segments || [])
    .map((segment) => segment.instruction || [segment.startStationName, segment.endStationName].filter(Boolean).join(" → "))
    .filter(Boolean)
    .join(" · ");
  const flags = [
    transit.provider,
    transit.fallbackUsed ? "fallback" : null,
    transit.realtimeStatus && transit.realtimeStatus !== "UNAVAILABLE" ? transit.realtimeStatus : null,
  ].filter(Boolean).join(" / ");
  return `
    <div class="transit-row" data-route-order="${transit.routeOrder}">
      <strong>${escapeHtml(transit.summary || `${transit.originName || "출발"} → ${transit.destinationName || "도착"}`)}</strong>
      <span>${transit.totalMinutes}분 · 도보 ${transit.walkMinutes || 0}분 · 환승 ${transit.transferCount || 0}회</span>
      <p>${escapeHtml(segments || flags)}${segments && flags ? ` · ${escapeHtml(flags)}` : ""}</p>
    </div>
  `;
}

function endpoint(location, time, label) {
  return `
    <div class="timeline-item endpoint">
      <time class="timeline-time">${formatTime(time)}</time>
      <div class="timeline-rail"><span class="timeline-node"></span></div>
      <div class="timeline-body"><h3>${escapeHtml(location?.name || label)}</h3><p>${label}</p></div>
    </div>
  `;
}

export function renderTimeline(day) {
  if (!day) return "";
  const stops = (day.stops || []).map((stop) => `
    ${transitDetails(stop.inboundTransit)}
    <div class="timeline-item">
      <time class="timeline-time">${formatTime(stop.arriveAt)}<br>${formatTime(stop.departAt)}</time>
      <div class="timeline-rail"><span class="timeline-node"></span></div>
      <div class="timeline-body">
        <h3>${stop.order}. ${escapeHtml(stop.place?.name)}</h3>
        <p>${escapeHtml([stop.place?.category, stop.place?.address, `${stop.stayMinutes}분 체류`].filter(Boolean).join(" · "))}</p>
        ${stop.place?.operatingInfo?.openingHoursText ? `<p>운영 ${escapeHtml(stop.place.operatingInfo.openingHoursText)}</p>` : ""}
        <div class="timeline-meta">${renderTags(stop)}</div>
      </div>
    </div>
  `).join("");

  return `
    <div class="day-heading"><h2>${day.dayNo}일차 · ${escapeHtml(formatDate(day.date))}</h2><p>${escapeHtml(day.summary)}</p></div>
    ${endpoint(day.startLocation, day.startTime, "출발")}
    ${stops}
    ${transitDetails(day.finalTransit)}
    ${endpoint(day.endLocation, day.endTime, "도착")}
  `;
}

export function formatJson(value) {
  return JSON.stringify(value ?? {}, null, 2);
}

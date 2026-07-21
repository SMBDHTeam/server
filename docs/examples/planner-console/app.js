import { ApiError, PlannerApiClient } from "./api-client.js";
import { renderMap } from "./map-renderer.js";
import { formatJson, renderDayTabs, renderEvaluation, renderSummary, renderTimeline } from "./schedule-renderer.js";

const LOCATION_PRESETS = {
  station: { name: "부산역", longitude: 129.0403, latitude: 35.1151 },
  haeundae: { name: "해운대 숙소", longitude: 129.1580, latitude: 35.1590 },
  nampo: { name: "남포동 숙소", longitude: 129.0320, latitude: 35.1000 },
  airport: { name: "김해국제공항", longitude: 128.9485, latitude: 35.1732 },
};

const PREFERRED_ANSWERS = {
  COMPANION: "COMPANION_PARENTS",
  PACE: "PACE_BALANCED",
  THEME: "THEME_CULTURE",
  MOBILITY: "MOBILITY_LOW_WALK",
  TRANSIT: "TRANSIT_SIMPLE",
};

const escapeHtml = (value) => String(value ?? "")
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;")
  .replaceAll("'", "&#039;");

const state = {
  questions: [],
  request: null,
  schedule: null,
  mapsByDay: new Map(),
  activeDayNo: 1,
  activeJson: "request",
};

const elements = {
  apiBase: document.querySelector("#api-base"),
  connectButton: document.querySelector("#connect-button"),
  connectionStatus: document.querySelector("#connection-status"),
  form: document.querySelector("#planner-form"),
  startDate: document.querySelector("#start-date"),
  endDate: document.querySelector("#end-date"),
  dailyStartTime: document.querySelector("#daily-start-time"),
  dailyEndTime: document.querySelector("#daily-end-time"),
  tripDuration: document.querySelector("#trip-duration"),
  dayConditions: document.querySelector("#day-conditions"),
  dayTemplate: document.querySelector("#day-condition-template"),
  resetDaysButton: document.querySelector("#reset-days-button"),
  questions: document.querySelector("#questions"),
  questionCount: document.querySelector("#question-count"),
  mustVisitIds: document.querySelector("#must-visit-ids"),
  generateButton: document.querySelector("#generate-button"),
  requestTime: document.querySelector("#request-time"),
  scheduleId: document.querySelector("#schedule-id"),
  resultEmpty: document.querySelector("#result-empty"),
  resultContent: document.querySelector("#result-content"),
  summaryStrip: document.querySelector("#summary-strip"),
  evaluationPanel: document.querySelector("#evaluation-panel"),
  dayTabs: document.querySelector("#day-tabs"),
  timeline: document.querySelector("#timeline"),
  mapCanvas: document.querySelector("#map-canvas"),
  mapLegend: document.querySelector("#map-legend"),
  jsonOutput: document.querySelector("#json-output"),
  errorPanel: document.querySelector("#error-panel"),
  errorTitle: document.querySelector("#error-title"),
  errorMessage: document.querySelector("#error-message"),
  errorDetails: document.querySelector("#error-details"),
};

function localDate(offsetDays) {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"), String(date.getDate()).padStart(2, "0")].join("-");
}

function tripDayCount() {
  const start = new Date(`${elements.startDate.value}T00:00:00`);
  const end = new Date(`${elements.endDate.value}T00:00:00`);
  const days = Math.floor((end - start) / 86400000) + 1;
  return Number.isFinite(days) && days > 0 ? Math.min(days, 7) : 0;
}

function dayEndpoints(dayNo, dayCount) {
  if (dayCount === 1) return [LOCATION_PRESETS.station, LOCATION_PRESETS.airport];
  const intermediate = [LOCATION_PRESETS.haeundae, LOCATION_PRESETS.nampo];
  const start = dayNo === 1 ? LOCATION_PRESETS.station : intermediate[(dayNo - 2) % intermediate.length];
  const end = dayNo === dayCount ? LOCATION_PRESETS.airport : intermediate[(dayNo - 1) % intermediate.length];
  return [start, end];
}

function setLocation(editor, value) {
  editor.querySelector('[data-field="name"]').value = value.name;
  editor.querySelector('[data-field="longitude"]').value = value.longitude;
  editor.querySelector('[data-field="latitude"]').value = value.latitude;
}

function renderDayConditions() {
  const count = tripDayCount();
  elements.tripDuration.textContent = count ? `${count}일${count === 7 && dateDifference() + 1 > 7 ? " (최대 7일)" : ""}` : "날짜 확인";
  elements.dayConditions.replaceChildren();
  for (let dayNo = 1; dayNo <= count; dayNo += 1) {
    const fragment = elements.dayTemplate.content.cloneNode(true);
    const fieldset = fragment.querySelector("fieldset");
    const [startLocation, endLocation] = dayEndpoints(dayNo, count);
    fieldset.dataset.dayNo = dayNo;
    fieldset.querySelector("legend").textContent = `${dayNo}일차`;
    fieldset.querySelector('[data-field="startTime"]').value = elements.dailyStartTime.value;
    fieldset.querySelector('[data-field="endTime"]').value = dayNo === count ? "17:00" : elements.dailyEndTime.value;
    setLocation(fieldset.querySelector('[data-location="startLocation"]'), startLocation);
    setLocation(fieldset.querySelector('[data-location="endLocation"]'), endLocation);
    elements.dayConditions.append(fieldset);
  }
}

function dateDifference() {
  return Math.floor((new Date(`${elements.endDate.value}T00:00:00`) - new Date(`${elements.startDate.value}T00:00:00`)) / 86400000);
}

function client() {
  return new PlannerApiClient(elements.apiBase.value.trim());
}

function setConnection(stateName, label) {
  elements.connectionStatus.dataset.state = stateName;
  elements.connectionStatus.textContent = label;
}

async function loadQuestions() {
  hideError();
  setConnection("loading", "확인 중");
  elements.connectButton.disabled = true;
  try {
    const response = await client().getTripQuestions();
    state.questions = response.items || [];
    renderQuestions();
    setConnection("ok", "연결됨");
  } catch (error) {
    setConnection("error", "연결 실패");
    showError(error, "API 연결 실패");
  } finally {
    elements.connectButton.disabled = false;
  }
}

function renderQuestions() {
  elements.questionCount.textContent = `${state.questions.length}개 질문`;
  elements.questions.innerHTML = state.questions.map((question) => {
    const preferred = PREFERRED_ANSWERS[question.id];
    const answers = question.answers.map((answer, index) => `
      <label class="answer-option">
        <input type="radio" name="question-${question.id}" value="${answer.id}" data-question-id="${question.id}"
          ${answer.id === preferred || (!preferred && index === 0) ? "checked" : ""} ${question.required ? "required" : ""}>
        <span>${answer.label}</span>
      </label>
    `).join("");
    return `<fieldset class="question-group"><legend>${question.text}</legend><div class="answer-options">${answers}</div></fieldset>`;
  }).join("");
}

function readLocation(editor) {
  return {
    name: editor.querySelector('[data-field="name"]').value.trim(),
    longitude: Number(editor.querySelector('[data-field="longitude"]').value),
    latitude: Number(editor.querySelector('[data-field="latitude"]').value),
  };
}

function readDayConditions() {
  return [...elements.dayConditions.querySelectorAll(".day-condition")].map((fieldset) => ({
    dayNo: Number(fieldset.dataset.dayNo),
    startTime: fieldset.querySelector('[data-field="startTime"]').value,
    endTime: fieldset.querySelector('[data-field="endTime"]').value,
    startLocation: readLocation(fieldset.querySelector('[data-location="startLocation"]')),
    endLocation: readLocation(fieldset.querySelector('[data-location="endLocation"]')),
  }));
}

function buildRequest() {
  const days = readDayConditions();
  const selectedAnswers = [...elements.questions.querySelectorAll('input[type="radio"]:checked')]
    .map((input) => ({ questionId: input.dataset.questionId, answerId: input.value }));
  const mustVisitPlaceIds = elements.mustVisitIds.value.split(",")
    .map((value) => value.trim())
    .filter(Boolean)
    .map(Number);
  return {
    startDate: elements.startDate.value,
    endDate: elements.endDate.value,
    dailyStartTime: elements.dailyStartTime.value,
    dailyEndTime: elements.dailyEndTime.value,
    startLocation: days[0].startLocation,
    endLocation: days.at(-1).endLocation,
    selectedAnswers,
    mustVisitPlaceIds,
    days,
  };
}

async function createSchedule(event) {
  event.preventDefault();
  hideError();
  if (!elements.form.reportValidity()) return;
  if (!state.questions.length) {
    await loadQuestions();
    if (!state.questions.length) return;
  }
  const payload = buildRequest();
  if (payload.mustVisitPlaceIds.some((id) => !Number.isSafeInteger(id) || id <= 0)) {
    showError(new Error("필수 방문 장소 ID는 쉼표로 구분한 양의 정수여야 합니다."), "입력 확인");
    return;
  }

  state.request = payload;
  state.schedule = null;
  state.mapsByDay.clear();
  elements.generateButton.disabled = true;
  elements.generateButton.textContent = "Planner 실행 중";
  elements.requestTime.textContent = "";
  setConnection("loading", "생성 중");
  const startedAt = performance.now();

  try {
    state.schedule = await client().createSchedule(payload);
    state.activeDayNo = state.schedule.days?.[0]?.dayNo || 1;
    await loadMapForDay(state.activeDayNo);
    renderResult();
    const seconds = ((performance.now() - startedAt) / 1000).toFixed(2);
    elements.requestTime.textContent = `${seconds}초`;
    setConnection("ok", "생성 완료");
  } catch (error) {
    setConnection("error", "생성 실패");
    showError(error, "일정 생성 실패");
  } finally {
    elements.generateButton.disabled = false;
    elements.generateButton.textContent = "일정 생성";
  }
}

async function loadMapForDay(dayNo) {
  if (!state.schedule || state.mapsByDay.has(dayNo)) return;
  const mapData = await client().getScheduleMap(state.schedule.id, dayNo);
  state.mapsByDay.set(dayNo, mapData);
}

function renderResult() {
  elements.resultEmpty.hidden = true;
  elements.resultContent.hidden = false;
  elements.scheduleId.textContent = state.schedule.id;
  elements.scheduleId.title = state.schedule.id;
  elements.summaryStrip.innerHTML = renderSummary(state.schedule);
  elements.evaluationPanel.innerHTML = renderEvaluation(state.schedule.evaluation);
  elements.dayTabs.innerHTML = renderDayTabs(state.schedule.days || [], state.activeDayNo);
  const activeDay = state.schedule.days.find((day) => day.dayNo === state.activeDayNo);
  elements.timeline.innerHTML = renderTimeline(activeDay);
  renderJson();
  if (document.querySelector('.view-tab[data-view="map"]').classList.contains("is-active")) renderActiveMap();
}

function activeDayProviders() {
  const day = state.schedule?.days?.find((item) => item.dayNo === state.activeDayNo);
  return [...(day?.stops || []).map((stop) => stop.inboundTransit), day?.finalTransit]
    .filter(Boolean)
    .map((transit) => transit.provider)
    .filter(Boolean);
}

function renderMapLegend(mapData, source) {
  const providers = [...new Set(activeDayProviders())];
  const fallbackCount = (mapData.routeLines || []).filter((line) => line.fallbackUsed).length;
  const modes = [...new Set((mapData.routeLines || []).map((line) => line.mode))];
  const modeLabel = { WALK: "도보", BUS: "버스", SUBWAY: "도시철도", TRANSIT: "대중교통" };
  const providerClass = providers.includes("FAKE") ? "provider-fake" : "provider-live";
  const routes = (mapData.routeLines || [])
    .filter((line) => line.mode !== "WALK")
    .map((line) => line.lineName)
    .filter(Boolean);
  const routeNames = [...new Set(routes)].slice(0, 6).join(" · ");
  elements.mapLegend.innerHTML = `
    <div class="legend-heading">
      <strong>${state.activeDayNo}일차</strong>
      <span class="provider-badge ${providerClass}">${escapeHtml(providers.join(" + ") || "UNKNOWN")}</span>
      <span>${escapeHtml(source)}</span>
    </div>
    <div class="legend-modes">
      ${modes.map((mode) => `<span><i data-mode="${escapeHtml(mode)}"></i>${escapeHtml(modeLabel[mode] || mode)}</span>`).join("")}
      ${fallbackCount ? `<span class="fallback-label">fallback ${fallbackCount}구간</span>` : ""}
    </div>
    ${routeNames ? `<div class="route-names">${escapeHtml(routeNames)}</div>` : ""}
  `;
}

async function changeDay(dayNo) {
  state.activeDayNo = dayNo;
  try {
    await loadMapForDay(dayNo);
    renderResult();
  } catch (error) {
    showError(error, `${dayNo}일차 지도 조회 실패`);
  }
}

async function renderActiveMap() {
  const mapData = state.mapsByDay.get(state.activeDayNo);
  if (!mapData) return;
  elements.mapLegend.textContent = `${state.activeDayNo}일차 · 지도 준비 중`;
  const source = await renderMap(elements.mapCanvas, mapData);
  renderMapLegend(mapData, source);
}

function switchView(view) {
  document.querySelectorAll(".view-tab").forEach((button) => {
    const active = button.dataset.view === view;
    button.classList.toggle("is-active", active);
    button.setAttribute("aria-selected", String(active));
  });
  document.querySelectorAll("[data-view-pane]").forEach((pane) => pane.classList.toggle("is-active", pane.dataset.viewPane === view));
  if (view === "map") renderActiveMap();
}

function renderJson() {
  const value = state.activeJson === "request"
    ? state.request
    : state.activeJson === "schedule"
      ? state.schedule
      : state.mapsByDay.get(state.activeDayNo);
  elements.jsonOutput.textContent = formatJson(value);
}

function showError(error, title) {
  elements.errorPanel.hidden = false;
  elements.errorTitle.textContent = title;
  elements.errorMessage.textContent = error.message;
  const details = error instanceof ApiError
    ? { status: error.status, url: error.url, response: error.body }
    : { name: error.name };
  elements.errorDetails.textContent = formatJson(details);
}

function hideError() {
  elements.errorPanel.hidden = true;
}

elements.startDate.value = localDate(1);
elements.endDate.value = localDate(3);
renderDayConditions();

elements.connectButton.addEventListener("click", loadQuestions);
elements.form.addEventListener("submit", createSchedule);
elements.resetDaysButton.addEventListener("click", renderDayConditions);
[elements.startDate, elements.endDate].forEach((input) => input.addEventListener("change", renderDayConditions));
[elements.dailyStartTime, elements.dailyEndTime].forEach((input) => input.addEventListener("change", renderDayConditions));
elements.dayTabs.addEventListener("click", (event) => {
  const button = event.target.closest("[data-day-no]");
  if (button) changeDay(Number(button.dataset.dayNo));
});
document.querySelectorAll(".view-tab").forEach((button) => button.addEventListener("click", () => switchView(button.dataset.view)));
document.querySelectorAll(".json-tab").forEach((button) => button.addEventListener("click", () => {
  state.activeJson = button.dataset.json;
  document.querySelectorAll(".json-tab").forEach((item) => item.classList.toggle("is-active", item === button));
  renderJson();
}));

loadQuestions();

export class ApiError extends Error {
  constructor(message, { status = 0, body = null, url = "" } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
    this.url = url;
  }
}

export class PlannerApiClient {
  constructor(baseUrl, timeoutMs = 120000) {
    this.baseUrl = baseUrl.replace(/\/$/, "");
    this.timeoutMs = timeoutMs;
  }

  getTripQuestions() {
    return this.request("/trip-questions");
  }

  createSchedule(payload) {
    return this.request("/schedules", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  }

  getScheduleMap(scheduleId, dayNo) {
    const query = dayNo ? `?dayNo=${encodeURIComponent(dayNo)}` : "";
    return this.request(`/schedules/${encodeURIComponent(scheduleId)}/map${query}`);
  }

  async request(path, options = {}) {
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), this.timeoutMs);
    const url = `${this.baseUrl}${path}`;

    try {
      const response = await fetch(url, {
        ...options,
        headers: {
          Accept: "application/json",
          ...(options.body ? { "Content-Type": "application/json" } : {}),
          ...options.headers,
        },
        signal: controller.signal,
      });
      const text = await response.text();
      const body = text ? this.parseJson(text, url) : null;

      if (!response.ok) {
        const message = body?.message || body?.error?.message || `${response.status} ${response.statusText}`;
        throw new ApiError(message, { status: response.status, body, url });
      }
      return body;
    } catch (error) {
      if (error.name === "AbortError") {
        throw new ApiError(`${this.timeoutMs / 1000}초 안에 응답하지 않았습니다.`, { url });
      }
      if (error instanceof ApiError) throw error;
      throw new ApiError(error.message || "API에 연결할 수 없습니다.", { url });
    } finally {
      window.clearTimeout(timeoutId);
    }
  }

  parseJson(text, url) {
    try {
      return JSON.parse(text);
    } catch {
      throw new ApiError("서버가 JSON이 아닌 응답을 반환했습니다.", { body: text, url });
    }
  }
}

# Schedule FastAPI Migration

## Current Runtime Path

When `SCHEDULE_FASTAPI_ENABLED=true`, Spring schedule APIs delegate to FastAPI first and do not execute the internal planner path for these entry points:

- `POST /api/v1/schedule-previews`
- `GET /api/v1/schedule-previews/{previewId}`
- `POST /api/v1/schedules`
- `GET /api/v1/schedules`
- `GET /api/v1/schedules/{scheduleId}`
- `PATCH /api/v1/schedules/{scheduleId}`
- `GET /api/v1/schedules/{scheduleId}/map`

Delegation happens at the top of:

- `SchedulePreviewService.create`
- `SchedulePreviewService.get`
- `ScheduleV2Service.create`
- `ScheduleService.create`
- `ScheduleService.getAll`
- `ScheduleService.get`
- `ScheduleService.update`
- `ScheduleService.getMap`

## Safe Cleanup Order

Do not delete the Spring planner immediately. Remove code in this order.

1. Keep controllers and DTOs as the public contract while delegation is active.
2. Keep `FastApiScheduleClient` and related config/properties as the active runtime path.
3. Treat Spring planner internals as fallback-only code until production traffic is stable.
4. Remove unreachable planner-only branches after confirming no environment runs with `SCHEDULE_FASTAPI_ENABLED=false`.

## First Cleanup Targets

These areas are no longer in the hot path when delegation is enabled and should be reviewed first:

- Preview resolution and persistence logic used only by `SchedulePreviewService.create/get` internal path
- Idempotent preview-to-schedule orchestration in `ScheduleV2Service.create` internal path
- Planner generation flow under `ScheduleService.createInternal`
- Map rebuild logic under `ScheduleService.getMap` internal path
- Schedule update route recalculation under `ScheduleService.update` internal path

## Keep For Now

Do not remove these yet because they still protect compatibility or may still be referenced by non-delegated flows:

- Schedule/public DTOs returned by Spring controllers
- Error mapping and controller advice
- Share APIs that read schedules through `ScheduleService`
- Feature flag/config wiring for rollback

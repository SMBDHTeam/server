#!/usr/bin/env bash
set -euo pipefail

./gradlew test \
  --tests 'com.server.question.config.QuestionSeedInitializerTest' \
  --tests 'com.server.schedule.service.ScheduleRequestValidatorTest' \
  --tests 'com.server.schedule.evaluation.ScheduleHardGateEvaluatorTest' \
  --tests 'com.server.schedule.planner.DayPlaceAllocatorTest' \
  --tests 'com.server.schedule.planner.DayRouteOptimizerTest' \
  --tests 'com.server.schedule.service.ScheduleServiceTest.createUsesDaySpecificEndpoints' \
  --tests 'com.server.schedule.service.ScheduleServiceTest.createOptimizesVisitOrderWithRouteCache' \
  --tests 'com.server.schedule.service.ScheduleServiceTest.*Scenario'

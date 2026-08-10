#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_dir"

assert_contains() {
    local file="$1"
    local pattern="$2"
    local description="$3"
    if ! rg -q --fixed-strings "$pattern" "$file"; then
        echo "FAIL: $description"
        exit 1
    fi
    echo "PASS: $description"
}

task_dao="app/src/main/java/com/example/autotoucher/data/db/TaskDao.kt"
alarm_receiver="app/src/main/java/com/example/autotoucher/scheduler/AlarmReceiver.kt"
executor_service="app/src/main/java/com/example/autotoucher/service/TaskExecutorService.kt"
wake_activity="app/src/main/java/com/example/autotoucher/ui/WakeActivity.kt"
manifest="app/src/main/AndroidManifest.xml"

assert_contains "$task_dao" 'SELECT * FROM tasks ORDER BY id ASC' \
    "task list uses stable insertion order"
assert_contains "$alarm_receiver" 'TaskExecutorService.prepareWakeSession()' \
    "alarm receiver resets wake coordination before launch"
assert_contains "$alarm_receiver" 'context.startActivity(WakeActivity.buildIntent(context))' \
    "wake activity is launched directly from alarm delivery"

activity_line="$(rg -n --fixed-strings 'context.startActivity(WakeActivity.buildIntent(context))' "$alarm_receiver" | cut -d: -f1)"
service_line="$(rg -n --fixed-strings 'context.startForegroundService(serviceIntent)' "$alarm_receiver" | cut -d: -f1)"
if (( activity_line >= service_line )); then
    echo "FAIL: wake activity must be requested before handing work to the service"
    exit 1
fi
echo "PASS: wake request stays inside the alarm background-launch window"

assert_contains "$wake_activity" 'override fun onPostResume()' \
    "keyguard dismissal waits until the activity is visible"
assert_contains "$wake_activity" 'keyguardManager.requestDismissKeyguard(' \
    "non-secure keyguard dismissal is requested"
assert_contains "$wake_activity" 'finishAndRemoveTask()' \
    "transparent wake window is removed before gestures execute"
assert_contains "$executor_service" 'withTimeoutOrNull(WAKE_READY_TIMEOUT_MS)' \
    "wake coordination cannot hang forever"
assert_contains "$executor_service" 'PowerManager.ACQUIRE_CAUSES_WAKEUP' \
    "screen wake lock fallback is present"
assert_contains "$executor_service" 'releaseExecutionWakeLock()' \
    "screen wake lock is explicitly released"
assert_contains "$manifest" 'android.permission.WAKE_LOCK' \
    "wake lock permission is declared"
assert_contains "$manifest" 'android.permission.TURN_SCREEN_ON' \
    "Android 14+ screen-on permission is declared"
assert_contains "$manifest" 'android:turnScreenOn="true"' \
    "wake activity requests screen-on behavior"
assert_contains "$manifest" 'android:noHistory="true"' \
    "wake activity cannot remain in navigation history"

echo "All source-level smoke tests passed. No Android compilation was performed."

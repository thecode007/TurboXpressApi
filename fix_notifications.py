import re

with open("src/main/kotlin/com/thecode007/turboxpress/service/NotificationService.kt", "r") as f:
    content = f.read()

# Remove the locale, title, and body lines
content = re.sub(
    r"[ \t]*val locale = getUserLocale\([^)]+\)\n[ \t]*val title = getLocalizedMessage\([^)]+\)\n[ \t]*val body = getLocalizedMessage\([^)]+\)\n",
    "",
    content
)

# Remove the specific comment block in notifyBroadcastOrderTaken
comment_block = """                // Keep data-only if preferred, but for consistency we add notification.
                // The mobile app can choose to suppress it or we just let it show.
                // The previous code had a comment: "Data-only so onMessageReceived ALWAYS fires".
                // Adding notification payload might change behavior on Android when in background.
                // However, since we are doing proper notifications, let's keep it here.
"""
content = content.replace(comment_block, "")

# Remove the .setNotification(...) lines
content = re.sub(
    r"[ \t]*\.setNotification\(Notification\.builder\(\)\.setTitle\(title\)\.setBody\(body\)\.build\(\)\)\n",
    "\n",
    content
)

with open("src/main/kotlin/com/thecode007/turboxpress/service/NotificationService.kt", "w") as f:
    f.write(content)
